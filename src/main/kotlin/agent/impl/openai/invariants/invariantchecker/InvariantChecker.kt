package agent.impl.openai.invariants.invariantchecker

import agent.impl.openai.invariants.InvariantSet
import agent.impl.openai.invariants.InvariantSeverity
import agent.impl.openai.invariants.model.InvariantCheckResult
import agent.impl.openai.invariants.model.InvariantViolation

interface InvariantChecker {
    suspend fun checkUserRequest(
        invariants: InvariantSet,
        userText: String
    ): InvariantCheckResult

    suspend fun checkAssistantReply(
        invariants: InvariantSet,
        reply: String
    ): InvariantCheckResult
}

class RuleBasedInvariantChecker : InvariantChecker {

    override suspend fun checkUserRequest(
        invariants: InvariantSet,
        userText: String
    ): InvariantCheckResult {
        val text = userText.lowercase()
        val violations = mutableListOf<InvariantViolation>()

        for (inv in invariants.items) {
            if (inv.severity != InvariantSeverity.HARD) continue

            val title = inv.title.lowercase()
            val desc = inv.description.lowercase()

            val violates =
                ("ktor" in desc && "okhttp" in text) ||
                        ("без branching" in desc && "branch" in text) ||
                        ("не менять публичные интерфейсы" in desc && ("измени интерфейс" in text || "поменяй интерфейс" in text)) ||
                        ("не использовать okhttp" in desc && "okhttp" in text)

            if (violates) {
                violations += InvariantViolation(
                    invariantId = inv.id,
                    title = inv.title,
                    explanation = "Запрос пользователя конфликтует с инвариантом: ${inv.title}"
                )
            }
        }

        return InvariantCheckResult(
            allowed = violations.isEmpty(),
            violations = violations
        )
    }

    override suspend fun checkAssistantReply(
        invariants: InvariantSet,
        reply: String
    ): InvariantCheckResult {
        val text = reply.lowercase()
        val violations = mutableListOf<InvariantViolation>()

        for (inv in invariants.items) {
            if (inv.severity != InvariantSeverity.HARD) continue

            val desc = inv.description.lowercase()

            val violates =
                ("ktor" in desc && "okhttp" in text) ||
                        ("без branching" in desc && "branch" in text) ||
                        ("не использовать okhttp" in desc && "okhttp" in text)

            if (violates) {
                violations += InvariantViolation(
                    invariantId = inv.id,
                    title = inv.title,
                    explanation = "Ответ ассистента нарушает инвариант: ${inv.title}"
                )
            }
        }

        return InvariantCheckResult(
            allowed = violations.isEmpty(),
            violations = violations
        )
    }
}