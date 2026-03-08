package agent.impl.openai.memory.context.slider

import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.memory.context.ContextStrategy
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.userprofile.UserProfileRepository
import agent.impl.openai.userprofile.service.PersonalizationService
import store.ConversationState
import store.SessionId

class SlidingWindowStrategy(
    private val mf: MessageFactory,
    private val personalizationService: PersonalizationService,
    private val profileRepository: UserProfileRepository,
    private val sessionId: SessionId
) : ContextStrategy {

    override val mode = ContextMode.SLIDING_WINDOW

    override suspend fun onUserMessage(state: ConversationState): ConversationState = state

    override fun buildInputForLLM(
        state: ConversationState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>> {
        val profile = profileRepository.load(sessionId)
        val profileInstruction = personalizationService.buildProfileInstruction(profile)

        val messages = state.messages

        val head = mf.msg("developer", systemInstruction)

        val body = messages.filter { m ->
            val role = m["role"] as? String
            role != "developer" && role != "system"
        }

        val tail = if (keepLastN <= 0) body else body.takeLast(keepLastN)

        return listOf(
            head,
            mf.msg("developer", profileInstruction)
        ) + tail
    }
}