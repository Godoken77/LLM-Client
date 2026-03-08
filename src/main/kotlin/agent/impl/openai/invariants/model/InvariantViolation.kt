package agent.impl.openai.invariants.model

data class InvariantViolation(
    val invariantId: String,
    val title: String,
    val explanation: String
)

data class InvariantCheckResult(
    val allowed: Boolean,
    val violations: List<InvariantViolation> = emptyList()
)