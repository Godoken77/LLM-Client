package agent.impl.openai.taskstages

data class TaskStateMachine(
    val stage: TaskStage = TaskStage.PLANNING,
    val currentStep: String = "",
    val expectedAction: ExpectedAction = ExpectedAction.DEFINE_PLAN,
    val pausedFromStage: TaskStage? = null,
    val lastUserGoal: String = ""
)