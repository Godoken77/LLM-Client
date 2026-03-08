package agent.impl.openai.memory.context.sticky

import com.google.gson.Gson
import com.google.gson.JsonObject
import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.model.ModelVersion

interface FactsUpdater {
    suspend fun updateFacts(
        existingFacts: Map<String, String>,
        lastUserMessage: String,
        recentDialog: List<Map<String, Any>>
    ): Map<String, String>
}

class FactsUpdaterImpl(
    private val openai: OpenaiApi,
    private val mf: MessageFactory,
    private val gson: Gson = Gson(),
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION
) : FactsUpdater {

    override suspend fun updateFacts(
        existingFacts: Map<String, String>,
        lastUserMessage: String,
        recentDialog: List<Map<String, Any>>
    ): Map<String, String> {
        val existing = existingFacts.entries.joinToString("\n") { "${it.key}: ${it.value}" }

        val summaryMsg = recentDialog.firstOrNull { m ->
            val role = m["role"] as? String
            role == "developer" && mf.extractText(m).startsWith("SUMMARY")
        }

        val tail = recentDialog.takeLast(10)

        val dialogText = buildList {
            if (summaryMsg != null) add(summaryMsg)   // ✅ всегда включаем summary
            addAll(tail)                              // ✅ + последние N сообщений
        }.joinToString("\n") { m ->
            val role = m["role"] as? String ?: "unknown"
            "${role.uppercase()}: ${mf.extractText(m)}"
        }

        val prompt = """
Ты ведёшь память facts (key-value) по диалогу. Обнови факты после нового сообщения пользователя.

Текущие facts:
$existing

Последние сообщения диалога:
$dialogText

Новое сообщение пользователя:
$lastUserMessage

Верни ТОЛЬКО JSON-объект с обновлёнными facts (строки). 
- сохраняй важное: цель, ограничения, предпочтения, решения, договорённости
- удаляй/переопредели устаревшее
""".trimIndent()

        val req = mapOf(
            "model" to model.version,
            "input" to listOf(
                mf.msg("developer", "Ты — модуль памяти facts. Возвращай только JSON."),
                mf.msg("user", prompt)
            ),
            "reasoning" to mapOf("effort" to "low")
        )

        val raw = openai.responses(req)
        if (raw.status !in 200..299) return existingFacts // не ломаем диалог

        return try {
            val root = gson.fromJson(raw.body, JsonObject::class.java)
            val jsonText = extractOutputText(root).trim()
            val obj = gson.fromJson(jsonText, JsonObject::class.java)

            buildMap {
                // сохраняем существующие, затем перекрываем новым
                putAll(existingFacts)
                for ((k, v) in obj.entrySet()) {
                    if (!v.isJsonNull) put(k, v.asString)
                }
            }
        } catch (_: Exception) {
            existingFacts
        }
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