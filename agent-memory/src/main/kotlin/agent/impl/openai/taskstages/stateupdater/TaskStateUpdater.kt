package agent.impl.openai.taskstages.stateupdater

import agent.impl.openai.memory.ports.MemoryLlmClient
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.taskstages.InvalidTaskTransitionException
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
    private val llmClient: MemoryLlmClient,
    private val messageFactory: MemoryMessageFactory,
    private val stateMachineService: TaskStateMachineService,
    private val gson: Gson = Gson(),
    private val model: String = "gpt-5.2"
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
Ты определяешь СОБЫТИЕ перехода конечного автомата задачи.

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
  "transition": "NO_CHANGE|START|APPROVE_PLAN|FINISH_EXECUTION|VALIDATION_OK|VALIDATION_FAIL|PAUSE|RESUME",
  "step": "...",
  "goal": "..."
}

Ограничения:
- нельзя перескакивать этапы
- нельзя делать реализацию до утверждённого плана
- нельзя завершать задачу без валидации
- если изменение состояния не требуется, верни NO_CHANGE
""".trimIndent()

        val req = mapOf(
            "model" to model,
            "input" to listOf(
                messageFactory.msg("developer", "Ты определяешь только событие перехода автомата. Возвращай только JSON."),
                messageFactory.msg("user", prompt)
            ),
            "reasoning" to mapOf("effort" to "low")
        )

        val raw = llmClient.responses(req)
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

        return try {
            stateMachineService.applyTransition(
                current = current,
                transition = decision.transition,
                step = decision.step,
                goal = decision.goal
            )
        } catch (_: InvalidTaskTransitionException) {
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
