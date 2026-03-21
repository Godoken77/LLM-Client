package agent.impl.openai.memory.adapters

import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.invariants.prompt.InvariantPromptBuilder
import agent.impl.openai.invariants.repository.InvariantRepository
import agent.impl.openai.memory.ports.MemoryInvariantService
import agent.impl.openai.memory.ports.MemoryLlmClient
import agent.impl.openai.memory.ports.MemoryLlmResponse
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.memory.ports.MemoryUserProfileService
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.userprofile.UserProfileRepository
import agent.impl.openai.userprofile.service.PersonalizationService

class OpenaiMemoryLlmClient(private val api: OpenaiApi) : MemoryLlmClient {
    override suspend fun responses(request: Map<String, Any>): MemoryLlmResponse {
        val r = api.responses(request)
        return MemoryLlmResponse(status = r.status, body = r.body)
    }
}

class MessageFactoryAdapter(private val mf: MessageFactory) : MemoryMessageFactory {
    override fun msg(role: String, text: String): Map<String, Any> = mf.msg(role, text)
    override fun extractText(message: Map<String, Any>): String = mf.extractText(message)
}

class UserProfileServiceAdapter(
    private val profileRepository: UserProfileRepository,
    private val personalizationService: PersonalizationService
) : MemoryUserProfileService {
    override fun buildProfileInstruction(userId: String): String {
        val profile = profileRepository.load(userId)
        return personalizationService.buildProfileInstruction(profile)
    }
}

class InvariantServiceAdapter(
    private val invariantRepository: InvariantRepository,
    private val invariantPromptBuilder: InvariantPromptBuilder
) : MemoryInvariantService {
    override fun buildInvariantPrompt(scopeId: String): String {
        val invariants = invariantRepository.load(scopeId)
        return invariantPromptBuilder.buildInstruction(invariants)
    }
}
