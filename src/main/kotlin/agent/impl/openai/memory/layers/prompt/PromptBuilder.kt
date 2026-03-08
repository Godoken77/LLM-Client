package agent.impl.openai.memory.layers.prompt

import agent.impl.openai.memory.layers.model.MemoryState
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.userprofile.UserProfileRepository
import agent.impl.openai.userprofile.service.PersonalizationService

class MemoryPromptBuilder(
    private val messageFactory: MessageFactory,
    private val personalizationService: PersonalizationService,
    private val profileRepository: UserProfileRepository
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

        input += messageFactory.msg("developer", systemInstruction)
        input += messageFactory.msg("developer", profileInstruction)

        if (memory.working.summary.isNotBlank()) {
            input += messageFactory.msg(
                "developer",
                "Рабочая память (текущая задача):\n${memory.working.summary}"
            )
        }

        if (memory.working.taskState.isNotEmpty()) {
            val text = memory.working.taskState.entries.joinToString("\n") {
                "- ${it.key}: ${it.value}"
            }
            input += messageFactory.msg("developer", "Task state:\n$text")
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