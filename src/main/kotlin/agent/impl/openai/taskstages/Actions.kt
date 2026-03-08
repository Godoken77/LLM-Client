package agent.impl.openai.taskstages

enum class ExpectedAction {
    DEFINE_PLAN,
    APPLY_CHANGE,
    RUN_CHECK,
    REVIEW_RESULT,
    WAIT_FOR_USER,
    FINALIZE,
    NONE
}