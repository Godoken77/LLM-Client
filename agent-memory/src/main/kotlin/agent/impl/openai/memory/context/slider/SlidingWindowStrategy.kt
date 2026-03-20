package agent.impl.openai.memory.context.slider

import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.memory.context.ContextStrategy
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.memory.ports.MemoryUserProfileService
import store.ConversationState

class SlidingWindowStrategy(
    private val mf: MemoryMessageFactory,
    private val userProfileService: MemoryUserProfileService,
    private val sessionId: String
) : ContextStrategy {

    override val mode = ContextMode.SLIDING_WINDOW

    override suspend fun onUserMessage(state: ConversationState): ConversationState = state

    override fun buildInputForLLM(
        state: ConversationState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>> {
        val profileInstruction = userProfileService.buildProfileInstruction(sessionId)

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
