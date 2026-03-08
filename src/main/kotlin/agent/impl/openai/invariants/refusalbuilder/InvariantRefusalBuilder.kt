package agent.impl.openai.invariants.refusalbuilder

import agent.impl.openai.invariants.model.InvariantCheckResult

interface InvariantRefusalBuilder {
    fun buildRefusal(userText: String, result: InvariantCheckResult): String
}

class InvariantRefusalBuilderImpl : InvariantRefusalBuilder {
    override fun buildRefusal(userText: String, result: InvariantCheckResult): String {
        if (result.allowed) return ""

        val violated = result.violations.joinToString("\n") {
            "- ${it.title}: ${it.explanation}"
        }

        return """
Я не могу предложить решение в таком виде, потому что запрос конфликтует с заданными инвариантами.

Нарушаемые инварианты:
$violated

Я могу помочь только в рамках этих ограничений и предложить альтернативное решение, которое им соответствует.
""".trimIndent()
    }
}