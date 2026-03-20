package agent.impl.openai.memory.layers.updater.longterm

import agent.impl.openai.memory.layers.model.LongTermMemory
import agent.impl.openai.memory.ports.MemoryLlmClient
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.model.ModelVersion
import com.google.gson.Gson
import com.google.gson.JsonObject

interface LongTermMemoryUpdater {
    suspend fun update(
        current: LongTermMemory,
        recentMessages: List<Map<String, Any>>
    ): LongTermMemory
}

class LongTermMemoryUpdaterImpl(
    private val llmClient: MemoryLlmClient,
    private val messageFactory: MemoryMessageFactory,
    private val gson: Gson = Gson(),
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION
) : LongTermMemoryUpdater {

    override suspend fun update(
        current: LongTermMemory,
        recentMessages: List<Map<String, Any>>
    ): LongTermMemory {
        val dialogText = recentMessages.takeLast(12).joinToString("\n") { m ->
            val role = m["role"] as? String ?: "unknown"
            val text = messageFactory.extractText(m)
            "${role.uppercase()}: $text"
        }

        val currentFacts = current.facts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val currentDecisions = current.decisions.joinToString("\n") { "- $it" }
        val currentKnowledge = current.knowledge.joinToString("\n") { "- $it" }

        val prompt = """
Ты обновляешь долговременную память ассистента.

Текущие facts:
$currentFacts

Текущие decisions:
$currentDecisions

Текущие knowledge:
$currentKnowledge

Последние сообщения:
$dialogText

Верни JSON:
{
  "facts": { ... },
  "decisions": [ ... ],
  "knowledge": [ ... ]
}

Сохраняй только устойчивые вещи:
- профиль
- предпочтения
- повторно используемые решения
- полезные постоянные знания

Не сохраняй временные детали текущей задачи.
""".trimIndent()

        val req = mapOf(
            "model" to model.version,
            "input" to listOf(
                messageFactory.msg("developer", "Ты обновляешь долговременную память. Верни только JSON."),
                messageFactory.msg("user", prompt)
            ),
            "reasoning" to mapOf("effort" to "low")
        )

        val raw = llmClient.responses(req)
        if (raw.status !in 200..299) return current

        return try {
            val root = gson.fromJson(raw.body, JsonObject::class.java)
            val jsonText = extractOutputText(root).trim()
            val obj = gson.fromJson(jsonText, JsonObject::class.java)

            val facts = current.facts.toMutableMap()
            obj.getAsJsonObject("facts")?.entrySet()?.forEach { (k, v) ->
                if (!v.isJsonNull) facts[k] = v.asString
            }

            val decisions = current.decisions.toMutableList()
            obj.getAsJsonArray("decisions")?.forEach {
                if (!it.isJsonNull) {
                    val value = it.asString
                    if (value !in decisions) decisions.add(value)
                }
            }

            val knowledge = current.knowledge.toMutableList()
            obj.getAsJsonArray("knowledge")?.forEach {
                if (!it.isJsonNull) {
                    val value = it.asString
                    if (value !in knowledge) knowledge.add(value)
                }
            }

            LongTermMemory(
                facts = facts,
                decisions = decisions,
                knowledge = knowledge
            )
        } catch (_: Exception) {
            current
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
