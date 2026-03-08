package agent.impl.openai.memory.layers.prompt

import agent.impl.openai.invariants.prompt.InvariantPromptBuilder
import agent.impl.openai.invariants.repository.InvariantRepository
import agent.impl.openai.memory.layers.model.MemoryState
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.userprofile.UserProfileRepository
import agent.impl.openai.userprofile.service.PersonalizationService

class MemoryPromptBuilder(
    private val messageFactory: MessageFactory,
    private val personalizationService: PersonalizationService,
    private val profileRepository: UserProfileRepository,
    private val invariantRepository: InvariantRepository,
    private val invariantPromptBuilder: InvariantPromptBuilder
) {
    fun buildInput(
        userId: String,
        memory: MemoryState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>> {
        val input = mutableListOf<Map<String, Any>>()

        val profile = profileRepository.load(userId)
        val profileInstruction = personalizationService.buildProfileInstruction(profile)

        val invariants = invariantRepository.load(userId)
        val invariantInstruction = invariantPromptBuilder.buildInstruction(invariants)

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