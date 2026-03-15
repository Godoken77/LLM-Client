package agent.impl.openai.responseparser

import com.google.gson.Gson
import com.google.gson.JsonObject
import agent.impl.openai.api.dto.Response
import agent.impl.openai.model.Answer
import agent.impl.openai.model.TokenStats

data class FunctionCall(
    val callId: String,
    val name: String,
    val argumentsJson: String
)

interface ResponseParser {
    fun parse(raw: Response): Answer
    fun extractFunctionCalls(raw: Response): List<FunctionCall>
}

class GsonResponseParserImpl(
    private val gson: Gson = Gson()
) : ResponseParser {

    override fun parse(raw: Response): Answer {
        if (raw.body.isBlank()) {
            return Answer("Пустой ответ от сервера. HTTP ${raw.status} ${raw.statusText}", null)
        }

        if (raw.status !in 200..299) {
            return Answer("Ошибка API: ${raw.status} ${raw.statusText}\n${raw.body}", null)
        }

        return try {
            val root = gson.fromJson(raw.body, JsonObject::class.java)

            val errEl = root.get("error")
            if (errEl != null && !errEl.isJsonNull && errEl.isJsonObject) {
                val err = errEl.asJsonObject
                val msg = err.get("message")?.takeIf { !it.isJsonNull }?.asString ?: "Unknown error"
                val type = err.get("type")?.takeIf { !it.isJsonNull }?.asString
                val code = err.get("code")?.takeIf { !it.isJsonNull }?.asString
                val details = buildString {
                    append("Ошибка API: ").append(msg)
                    if (!type.isNullOrBlank()) append("\nType: $type")
                    if (!code.isNullOrBlank()) append("\nCode: $code")
                }
                return Answer(details, null)
            }

            val reply = extractOutputText(root).trim().ifBlank { "(нет output_text)" }

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

            Answer(reply, stats)
        } catch (e: Exception) {
            Answer("Ошибка разбора ответа: ${e.message}\n${raw.body}", null)
        }
    }

    override fun extractFunctionCalls(raw: Response): List<FunctionCall> {
        if (raw.body.isBlank() || raw.status !in 200..299) return emptyList()
        return runCatching {
            val root = gson.fromJson(raw.body, JsonObject::class.java)
            val output = root.getAsJsonArray("output") ?: return emptyList()
            output.mapNotNull { item ->
                val obj = item.asJsonObject
                if (obj.get("type")?.asString != "function_call") return@mapNotNull null
                val callId = obj.get("call_id")?.asString ?: return@mapNotNull null
                val name = obj.get("name")?.asString ?: return@mapNotNull null
                val arguments = obj.get("arguments")?.asString ?: "{}"
                FunctionCall(callId, name, arguments)
            }
        }.getOrDefault(emptyList())
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
