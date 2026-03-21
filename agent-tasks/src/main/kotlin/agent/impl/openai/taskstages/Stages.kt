package agent.impl.openai.taskstages

enum class TaskStage {
    PLANNING,
    EXECUTION,
    VALIDATION,
    DONE,
    PAUSED
}

private val allowedTransitions: Map<TaskStage, Set<TaskTransition>> = mapOf(
    TaskStage.PLANNING to setOf(
        TaskTransition.START,
        TaskTransition.APPROVE_PLAN,
        TaskTransition.PAUSE,
        TaskTransition.NO_CHANGE
    ),
    TaskStage.EXECUTION to setOf(
        TaskTransition.FINISH_EXECUTION,
        TaskTransition.PAUSE,
        TaskTransition.NO_CHANGE
    ),
    TaskStage.VALIDATION to setOf(
        TaskTransition.VALIDATION_OK,
        TaskTransition.VALIDATION_FAIL,
        TaskTransition.PAUSE,
        TaskTransition.NO_CHANGE
    ),
    TaskStage.DONE to setOf(
        TaskTransition.NO_CHANGE
    ),
    TaskStage.PAUSED to setOf(
        TaskTransition.RESUME,
        TaskTransition.NO_CHANGE
    )
)
