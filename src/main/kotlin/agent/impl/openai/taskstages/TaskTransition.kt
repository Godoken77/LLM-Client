package agent.impl.openai.taskstages

enum class TaskTransition {
    NO_CHANGE,
    START,
    PAUSE,
    RESUME,
    NEXT_AFTER_PLANNING,
    NEXT_AFTER_EXECUTION,
    NEXT_AFTER_VALIDATION_OK,
    NEXT_AFTER_VALIDATION_FAIL
}

data class TaskTransitionDecision(
    val transition: TaskTransition,
    val step: String = "",
    val goal: String = ""
)