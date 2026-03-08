package agent.impl.openai.invariants.prompt

import agent.impl.openai.invariants.InvariantSet

interface InvariantPromptBuilder {
    fun buildInstruction(invariants: InvariantSet): String
}

class InvariantPromptBuilderImpl : InvariantPromptBuilder {

    override fun buildInstruction(invariants: InvariantSet): String {
        if (invariants.items.isEmpty()) {
            return "Инварианты отсутствуют."
        }

        val lines = mutableListOf<String>()
        lines += "Инварианты, которые нельзя нарушать без явного разрешения:"

        invariants.items.forEach { inv ->
            lines += "- [${inv.severity}] ${inv.type}: ${inv.title}"
            lines += "  ${inv.description}"
        }

        lines += "Если запрос пользователя конфликтует с HARD-инвариантом:"
        lines += "- не предлагай нарушающее решение"
        lines += "- явно объясни, какой инвариант нарушается"
        lines += "- предложи безопасную альтернативу в рамках инвариантов"

        return lines.joinToString("\n")
    }
}

