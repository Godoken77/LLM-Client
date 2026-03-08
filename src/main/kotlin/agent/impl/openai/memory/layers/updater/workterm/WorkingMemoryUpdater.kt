package agent.impl.openai.memory.layers.updater.workterm

import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.memory.layers.model.WorkingMemory
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.model.ModelVersion
import com.google.gson.Gson
import com.google.gson.JsonObject

interface WorkingMemoryUpdater {
    suspend fun update(
        current: WorkingMemory,
        recentMessages: List<Map<String, Any>>
    ): WorkingMemory
}

class WorkingMemoryUpdaterImpl(
    private val openai: OpenaiApi,
    private val messageFactory: MessageFactory,
    private val gson: Gson = Gson(),
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION
) : WorkingMemoryUpdater {

    override suspend fun update(
        current: WorkingMemory,
        recentMessages: List<Map<String, Any>>
    ): WorkingMemory {
        val dialogText = recentMessages.takeLast(12).joinToString("\n") { m ->
            val role = m["role"] as? String ?: "unknown"
            val text = messageFactory.extractText(m)
            "${role.uppercase()}: $text"
        }

        val currentState = current.taskState.entries.joinToString("\n") {
            "${it.key}: ${it.value}"
        }

        val prompt = """
Ты обновляешь рабочую память ассистента для текущей задачи.

Текущий summary:
${current.summary}

Текущее taskState:
$currentState

Последние сообщения:
$dialogText

Верни JSON:
{
  "summary": "...",
  "taskState": {
    "goal": "...",
    "constraints": "...",
    "current_step": "...",
    "status": "..."
  }
}

Сохраняй только то, что относится к текущей задаче.
""".trimIndent()

        val req = mapOf(
            "model" to model.version,
            "input" to listOf(
                messageFactory.msg("developer", "Ты обновляешь рабочую память. Верни только JSON."),
                messageFactory.msg("user", prompt)
            ),
            "reasoning" to mapOf("effort" to "low")
        )

        val raw = openai.responses(req)
        if (raw.status !in 200..299) return current

        return try {
            val root = gson.fromJson(raw.body, JsonObject::class.java)
            val jsonText = extractOutputText(root).trim()
            val obj = gson.fromJson(jsonText, JsonObject::class.java)

            val summary = obj.get("summary")?.takeIf { !it.isJsonNull }?.asString ?: current.summary

            val taskStateObj = obj.getAsJsonObject("taskState")
            val taskState = current.taskState.toMutableMap()
            taskStateObj?.entrySet()?.forEach { (k, v) ->
                if (!v.isJsonNull) taskState[k] = v.asString
            }

            WorkingMemory(
                summary = summary,
                taskState = taskState
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