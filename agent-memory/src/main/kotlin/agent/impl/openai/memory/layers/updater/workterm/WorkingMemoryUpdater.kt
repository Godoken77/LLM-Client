package agent.impl.openai.memory.layers.updater.workterm

import agent.impl.openai.memory.layers.model.WorkingMemory
import agent.impl.openai.memory.ports.MemoryLlmClient
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.model.ModelVersion
import agent.impl.openai.taskstages.stateupdater.TaskStateUpdater
import com.google.gson.Gson
import com.google.gson.JsonObject

interface WorkingMemoryUpdater {
    suspend fun update(
        current: WorkingMemory,
        recentMessages: List<Map<String, Any>>
    ): WorkingMemory
}

class WorkingMemoryUpdaterImpl(
    private val llmClient: MemoryLlmClient,
    private val messageFactory: MemoryMessageFactory,
    private val taskStateUpdater: TaskStateUpdater,
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

        val prompt = """
Ты обновляешь рабочую память ассистента для текущей задачи.

Текущий summary:
${current.summary}

Последние сообщения:
$dialogText

Верни JSON:
{
  "summary": "..."
}

Сохраняй только информацию по текущей задаче.
""".trimIndent()

        val req = mapOf(
            "model" to model.version,
            "input" to listOf(
                messageFactory.msg("developer", "Ты обновляешь рабочую память. Верни только JSON."),
                messageFactory.msg("user", prompt)
            ),
            "reasoning" to mapOf("effort" to "low")
        )

        val raw = llmClient.responses(req)
        val newSummary = if (raw.status in 200..299) {
            try {
                val root = gson.fromJson(raw.body, JsonObject::class.java)
                val jsonText = extractOutputText(root).trim()
                val obj = gson.fromJson(jsonText, JsonObject::class.java)
                obj.get("summary")?.takeIf { !it.isJsonNull }?.asString ?: current.summary
            } catch (_: Exception) {
                current.summary
            }
        } else {
            current.summary
        }

        val newMachine = taskStateUpdater.update(current.stateMachine, recentMessages)

        return current.copy(
            summary = newSummary,
            stateMachine = newMachine
        )
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
