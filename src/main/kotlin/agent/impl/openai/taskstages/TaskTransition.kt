package agent.impl.openai.taskstages

enum class TaskTransition {
    START,
    APPROVE_PLAN,
    FINISH_EXECUTION,
    VALIDATION_OK,
    VALIDATION_FAIL,
    PAUSE,
    RESUME,
    NO_CHANGE
}

data class TaskTransitionDecision(
    val transition: TaskTransition,
    val step: String = "",
    val goal: String = ""
)

class InvalidTaskTransitionException(message: String) : IllegalStateException(message)

data class TaskTransitionResult(
    val state: TaskStateMachine,
    val accepted: Boolean,
    val message: String? = null
)