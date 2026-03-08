package agent.impl.openai.invariants

enum class InvariantType {
    ARCHITECTURE,
    TECH_DECISION,
    STACK_CONSTRAINT,
    BUSINESS_RULE
}

enum class InvariantSeverity {
    HARD,   // нельзя нарушать
    SOFT    // желательно не нарушать, но можно обсудить
}

data class AssistantInvariant(
    val id: String,
    val type: InvariantType,
    val title: String,
    val description: String,
    val severity: InvariantSeverity = InvariantSeverity.HARD
)

data class InvariantSet(
    val items: MutableList<AssistantInvariant> = mutableListOf()
)