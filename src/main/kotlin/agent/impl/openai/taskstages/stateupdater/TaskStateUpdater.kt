package agent.impl.openai.taskstages.stateupdater

import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.model.ModelVersion
import agent.impl.openai.taskstages.TaskStateMachine
import agent.impl.openai.taskstages.TaskTransition
import agent.impl.openai.taskstages.TaskTransitionDecision
import agent.impl.openai.taskstages.service.TaskStateMachineService
import com.google.gson.Gson
import com.google.gson.JsonObject

interface TaskStateUpdater {
    suspend fun update(
        current: TaskStateMachine,
        recentMessages: List<Map<String, Any>>
    ): TaskStateMachine
}

class TaskStateUpdaterImpl(
    private val openai: OpenaiApi,
    private val messageFactory: MessageFactory,
    private val stateMachineService: TaskStateMachineService,
    private val gson: Gson = Gson(),
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION
) : TaskStateUpdater {

    override suspend fun update(
        current: TaskStateMachine,
        recentMessages: List<Map<String, Any>>
    ): TaskStateMachine {
        val dialogText = recentMessages.takeLast(10).joinToString("\n") { m ->
            val role = m["role"] as? String ?: "unknown"
            "${role.uppercase()}: ${messageFactory.extractText(m)}"
        }

        val prompt = """
Ты определяешь переход конечного автомата задачи.

Текущее состояние:
- stage: ${current.stage}
- currentStep: ${current.currentStep}
- expectedAction: ${current.expectedAction}
- pausedFromStage: ${current.pausedFromStage}
- lastUserGoal: ${current.lastUserGoal}

Последние сообщения:
$dialogText

Верни только JSON:
{
  "transition": "NO_CHANGE|START|PAUSE|RESUME|NEXT_AFTER_PLANNING|NEXT_AFTER_EXECUTION|NEXT_AFTER_VALIDATION_OK|NEXT_AFTER_VALIDATION_FAIL",
  "step": "...",
  "goal": "..."
}

Правила:
- planning -> execution -> validation -> done
- можно поставить на паузу из planning/execution/validation
- если пользователь просит продолжить, возвращайся из paused к pausedFromStage
- не возвращай финальный state
- если ничего менять не нужно, верни NO_CHANGE
""".trimIndent()

        val req = mapOf(
            "model" to model.version,
            "input" to listOf(
                messageFactory.msg("developer", "Ты определяешь переход конечного автомата задачи. Возвращай только JSON."),
                messageFactory.msg("user", prompt)
            ),
            "reasoning" to mapOf("effort" to "low")
        )

        val raw = openai.responses(req)
        if (raw.status !in 200..299) return current

        val decision = try {
            val root = gson.fromJson(raw.body, JsonObject::class.java)
            val jsonText = extractOutputText(root).trim()
            val obj = gson.fromJson(jsonText, JsonObject::class.java)

            TaskTransitionDecision(
                transition = obj.get("transition")?.asString
                    ?.let { TaskTransition.valueOf(it) }
                    ?: TaskTransition.NO_CHANGE,
                step = obj.get("step")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                goal = obj.get("goal")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            )
        } catch (_: Exception) {
            return current
        }

        return stateMachineService.applyTransition(current, decision)
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