package agent.impl.openai.memory.adapters

import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.compsessor.ConversationCompressor
import agent.impl.openai.invariants.prompt.InvariantPromptBuilder
import agent.impl.openai.invariants.repository.InvariantRepository
import agent.impl.openai.memory.ports.MemoryConversationCompressor
import agent.impl.openai.memory.ports.MemoryContextPromptBuilder
import agent.impl.openai.memory.ports.MemoryInvariantService
import agent.impl.openai.memory.ports.MemoryLlmClient
import agent.impl.openai.memory.ports.MemoryLlmResponse
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.memory.ports.MemoryStateNormalizer
import agent.impl.openai.memory.ports.MemoryUserProfileService
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.prompt.PromptBuilder
import agent.impl.openai.statenormalizer.StateNormalizer
import agent.impl.openai.userprofile.UserProfileRepository
import agent.impl.openai.userprofile.service.PersonalizationService
import store.ConversationState

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

class CompressorAdapter(private val compressor: ConversationCompressor) : MemoryConversationCompressor {
    override suspend fun maybeCompress(state: ConversationState): ConversationState =
        compressor.maybeCompress(state)
}

class StateNormalizerAdapter(private val normalizer: StateNormalizer) : MemoryStateNormalizer {
    override fun normalize(state: ConversationState): ConversationState =
        normalizer.normalize(state)
}

class PromptBuilderAdapter(private val prompts: PromptBuilder) : MemoryContextPromptBuilder {
    override fun ensureDeveloper(state: ConversationState, systemInstruction: String): ConversationState =
        prompts.ensureDeveloper(state, systemInstruction)

    override fun appendUser(state: ConversationState, userText: String): ConversationState =
        prompts.appendUser(state, userText)

    override fun buildInput(state: ConversationState): List<Map<String, Any>> =
        prompts.buildInput(state)
}
