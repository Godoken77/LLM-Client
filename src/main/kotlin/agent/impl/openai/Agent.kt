package org.example.agent.impl.openai

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.example.agent.Agent
import org.example.agent.impl.openai.api.OpenaiApi
import org.example.agent.impl.openai.api.dto.Response
import org.example.agent.impl.openai.model.Answer
import org.example.agent.impl.openai.model.ModelInstruction
import org.example.agent.impl.openai.model.ModelReasoningEffort
import org.example.agent.impl.openai.model.ModelVersion
import org.example.model.TokenStats
import org.example.session.SessionLocks
import org.example.store.ConversationState
import org.example.store.ConversationStore
import kotlin.collections.get
import kotlin.math.min

class OpenAIChatAgent(
    private val sessionId: String,
    private val store: ConversationStore,
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION,
    private val reasoningEffort: ModelReasoningEffort = ModelReasoningEffort.DEFAULT_REASONING_EFFORT,
    private val systemInstruction: ModelInstruction = ModelInstruction.DEFAULT_SYSTEM_INSTRUCTION,
    private val openaiApi: OpenaiApi,
    // компрессия
    private val compressionEnabled: Boolean = true,
    private val keepLastN: Int = 12,
    private val summarizeChunkSize: Int = 10
) : Agent {

    private val gson = Gson()
    private val lock = SessionLocks.lockFor(sessionId)

    override fun ask(userText: String): Answer = synchronized(lock) {
        runBlocking {
            var state = store.loadState(sessionId)
            state = normalizeState(state)

            if (state.messages.isEmpty()) {
                state.messages.add(msg("developer", systemInstruction.instruction))
            } else {
                val firstRole = state.messages.firstOrNull()?.get("role") as? String
                if (firstRole != "developer" && firstRole != "system") {
                    state.messages.add(0, msg("developer", systemInstruction.instruction))
                }
            }

            // 3) Append user message
            state.messages.add(msg("user", userText))

            // 4) (optional) compress before calling model
            if (compressionEnabled) {
                state = maybeCompress(state) // внутри будет summarizeIntoSummary -> тоже сеть
            }

            // 5) Build input for model
            val inputForLLM = buildInputForLLM(state)

            // 6) token counting (через openaiApi)
            val tokensHistory = openaiApi.inputTokens(model.version, inputForLLM)

            val requestMap: Map<String, Any> = mapOf(
                "model" to model.version,
                "input" to inputForLLM,
                "reasoning" to mapOf("effort" to reasoningEffort.level, "summary" to "concise")
            )

            val assistantTextAndUsage = callResponsesViaApi(requestMap)

            // 7) Save assistant message
            state.messages.add(msg("assistant", assistantTextAndUsage.reply))

            // 8) Persist state
            store.saveState(sessionId, state)

            // 9) Return with stats
            val stats = assistantTextAndUsage.stats?.copy(historyTokens = tokensHistory)
            Answer(assistantTextAndUsage.reply, stats)
        }
    }

    override fun reset() = synchronized(lock) {
        store.delete(sessionId)
    }

    private fun maybeCompress(state: ConversationState): ConversationState {
        if (keepLastN <= 0) return state

        val msgs = state.messages.toMutableList()

        val head = mutableListOf<Map<String, Any>>()
        val body = mutableListOf<Map<String, Any>>()

        for ((idx, m) in msgs.withIndex()) {
            val role = m["role"] as? String ?: ""
            if (idx == 0 && (role == "developer" || role == "system")) head.add(m) else body.add(m)
        }

        if (body.size <= keepLastN) return state

        val old = body.subList(0, body.size - keepLastN).toList()
        val tail = body.subList(body.size - keepLastN, body.size).toMutableList()

        var newSummary = state.summary
        var cursor = 0
        while (cursor < old.size) {
            val chunk = old.subList(cursor, min(cursor + summarizeChunkSize, old.size))
            newSummary = summarizeIntoSummary(existingSummary = newSummary, chunk = chunk)
            cursor += summarizeChunkSize
        }

        return ConversationState(
            summary = newSummary,
            messages = (head + tail).toMutableList()
        )
    }

    private fun summarizeIntoSummary(existingSummary: String, chunk: List<Map<String, Any>>): String {
        val chunkText = chunk.joinToString("\n") { m ->
            val role = m["role"] as? String ?: "unknown"
            val text = extractTextFromMessage(m)
            "${role.uppercase()}: $text"
        }

        val prompt = """
Ты сжимаешь историю диалога.
Текущее summary (если пустое — значит его ещё нет):
$existingSummary

Новые сообщения, которые нужно добавить в summary:
$chunkText

Сформируй обновлённое summary:
- сохрани факты, предпочтения пользователя, принятые решения
- сохрани важные имена/числа/ограничения
- не пиши лишних деталей
- 8–15 строк максимум
""".trimIndent()

        val req = mapOf(
            "model" to model.version,
            "input" to listOf(
                msg("developer", "Ты — модуль суммаризации. Пиши только summary, без вступлений."),
                msg("user", prompt)
            ),
            "reasoning" to mapOf("effort" to "low")
        )

        // Важно: это сетевой вызов через openaiApi
        return runBlocking { callResponsesViaApi(req).reply.trim() }
    }

    private fun buildInputForLLM(state: ConversationState): List<Map<String, Any>> {
        val msgs = state.messages.toMutableList()

        if (state.summary.isNotBlank()) {
            val summaryMsg = msg("developer", "Сжатый контекст предыдущего диалога (summary):\n${state.summary}")
            if (msgs.isNotEmpty()) msgs.add(1, summaryMsg) else msgs.add(summaryMsg)
        }

        return msgs
    }

    private data class ResponsesCallResult(val reply: String, val stats: TokenStats?)

    /**
     * Единственная точка вызова OpenAI Responses через openaiApi.
     * Здесь мы:
     * 1) получаем JSON-строку от openaiApi
     * 2) если ошибка — формируем текст ошибки
     * 3) если ок — парсим reply и usage
     */
    private suspend fun callResponsesViaApi(requestMap: Map<String, Any>): ResponsesCallResult {
        val raw: Response = openaiApi.responses(requestMap)

        if (raw.body.isBlank()) {
            return ResponsesCallResult(
                reply = "Пустой ответ от сервера. HTTP ${raw.status} ${raw.statusText}",
                stats = null
            )
        }

        // Если HTTP не 2xx — вернём как раньше: код/статус + тело
        if (raw.status !in 200..299) {
            return ResponsesCallResult(
                reply = "Ошибка API: ${raw.status} ${raw.statusText}\n${raw.body}",
                stats = null
            )
        }

        return try {
            val root = gson.fromJson(raw.body, JsonObject::class.java)

            // --- error (может быть null) ---
            val errEl = root.get("error")
            if (errEl != null && !errEl.isJsonNull && errEl.isJsonObject) {
                val err = errEl.asJsonObject
                val msg = err.get("message")?.takeIf { !it.isJsonNull }?.asString ?: "Unknown error"
                val type = err.get("type")?.takeIf { !it.isJsonNull }?.asString
                val code = err.get("code")?.takeIf { !it.isJsonNull }?.asString

                val details = buildString {
                    append("Ошибка API: ")
                    append(msg)
                    if (!type.isNullOrBlank()) append("\nType: $type")
                    if (!code.isNullOrBlank()) append("\nCode: $code")
                }
                return ResponsesCallResult(details, null)
            }

            // --- reply ---
            val reply = extractOutputText(root).trim().ifBlank { "(нет output_text)" }

            // --- usage (безопасно) ---
            val usageEl = root.get("usage")
            val usageObj = if (usageEl != null && usageEl.isJsonObject) usageEl.asJsonObject else null

            fun JsonObject.intOrNull(name: String): Int? {
                val el = get(name) ?: return null
                if (el.isJsonNull) return null
                return runCatching { el.asInt }.getOrNull()
            }

            val stats = usageObj?.let { u ->
                TokenStats(
                    userTokens = 0,
                    historyTokens = u.intOrNull("input_tokens") ?: 0,
                    responseTokens = u.intOrNull("output_tokens") ?: 0,
                    reasoningTokens = u.intOrNull("reasoning_tokens"),
                    totalTokens = u.intOrNull("total_tokens")
                )
            }

            ResponsesCallResult(reply, stats)
        } catch (e: Exception) {
            ResponsesCallResult(
                reply = "Ошибка разбора ответа: ${e.message}\n${raw.body}",
                stats = null
            )
        }
    }

    private fun msg(role: String, text: String): Map<String, Any> {
        val contentType = if (role == "assistant") "output_text" else "input_text"
        return mapOf("role" to role, "content" to listOf(mapOf("type" to contentType, "text" to text)))
    }

    private fun extractTextFromMessage(m: Map<String, Any>): String {
        val content = m["content"] as? List<*> ?: return ""
        val first = content.firstOrNull() as? Map<*, *> ?: return ""
        return first["text"] as? String ?: ""
    }

    private fun normalizeState(state: ConversationState): ConversationState {
        val msgs = state.messages.toMutableList()
        for (i in msgs.indices) {
            val role = msgs[i]["role"] as? String ?: continue
            val text = extractTextFromMessage(msgs[i])
            val desiredType = when (role) {
                "assistant" -> "output_text"
                "user", "developer", "system" -> "input_text"
                else -> continue
            }
            val content = msgs[i]["content"] as? List<*> ?: continue
            val first = content.firstOrNull() as? Map<*, *> ?: continue
            val t = first["type"] as? String ?: ""
            if (role == "assistant" && t == "refusal") continue
            if (t != desiredType) {
                msgs[i] = mapOf(
                    "role" to role,
                    "content" to listOf(mapOf("type" to desiredType, "text" to text))
                )
            }
        }
        return state.copy(messages = msgs)
    }

    private fun extractOutputText(root: JsonObject): String {
        val output = root.getAsJsonArray("output") ?: return ""
        val sb = StringBuilder()
        for (item in output) {
            val obj = item.asJsonObject
            if (obj.get("type")?.asString != "message") continue
            val contentArr = obj.getAsJsonArray("content") ?: continue
            for (c in contentArr) {
                val cobj = c.asJsonObject
                if (cobj.get("type")?.asString == "output_text") {
                    sb.append(cobj.get("text")?.asString ?: "")
                }
            }
        }
        return sb.toString()
    }
}