package agent.impl.openai.taskstages

enum class ExpectedAction {
    DEFINE_PLAN,
    APPROVE_PLAN,
    APPLY_CHANGE,
    RUN_CHECK,
    WAIT_FOR_USER,
    FINALIZE,
    NONE
}
