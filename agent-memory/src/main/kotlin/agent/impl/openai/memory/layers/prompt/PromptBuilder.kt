package agent.impl.openai.memory.layers.prompt

import agent.impl.openai.memory.layers.model.MemoryState
import agent.impl.openai.memory.ports.MemoryInvariantService
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.memory.ports.MemoryUserProfileService

class MemoryPromptBuilder(
    private val messageFactory: MemoryMessageFactory,
    private val userProfileService: MemoryUserProfileService,
    private val invariantService: MemoryInvariantService
) {
    fun buildInput(
        userId: String,
        memory: MemoryState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>> {
        val input = mutableListOf<Map<String, Any>>()

        val profileInstruction = userProfileService.buildProfileInstruction(userId)
        val invariantInstruction = invariantService.buildInvariantPrompt(userId)

        input += messageFactory.msg("developer", systemInstruction)
        input += messageFactory.msg("developer", profileInstruction)
        input += messageFactory.msg("developer", invariantInstruction)

        val machine = memory.working.stateMachine
        input += messageFactory.msg(
            "developer",
            """
Состояние задачи:
- stage: ${machine.stage}
- currentStep: ${machine.currentStep}
- expectedAction: ${machine.expectedAction}
- pausedFromStage: ${machine.pausedFromStage}
- lastUserGoal: ${machine.lastUserGoal}

Правило:
- нельзя перепрыгивать этапы жизненного цикла
- нельзя делать реализацию до утверждённого плана
- нельзя завершать задачу без валидации
""".trimIndent()
        )

        if (memory.working.summary.isNotBlank()) {
            input += messageFactory.msg(
                "developer",
                "Рабочая память (текущая задача):\n${memory.working.summary}"
            )
        }

        if (memory.longTerm.facts.isNotEmpty()) {
            val text = memory.longTerm.facts.entries.joinToString("\n") {
                "- ${it.key}: ${it.value}"
            }
            input += messageFactory.msg("developer", "Долговременные факты:\n$text")
        }

        if (memory.longTerm.decisions.isNotEmpty()) {
            val text = memory.longTerm.decisions.joinToString("\n") { "- $it" }
            input += messageFactory.msg("developer", "Ранее принятые решения:\n$text")
        }

        if (memory.longTerm.knowledge.isNotEmpty()) {
            val text = memory.longTerm.knowledge.joinToString("\n") { "- $it" }
            input += messageFactory.msg("developer", "Полезные знания:\n$text")
        }

        val shortBody = memory.shortTerm.messages
            .filter {
                val role = it["role"] as? String
                role != "developer" && role != "system"
            }
            .takeLast(keepLastN)

        input += shortBody

        return input
    }
}
