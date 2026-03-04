package agent.impl.openai.context.slider

import agent.impl.openai.context.ContextMode
import agent.impl.openai.context.ContextStrategy
import agent.impl.openai.messages.MessageFactory
import store.ConversationState

class SlidingWindowStrategy(
    private val mf: MessageFactory
) : ContextStrategy {

    override val mode = ContextMode.SLIDING_WINDOW

    override suspend fun onUserMessage(state: ConversationState): ConversationState {
        return state
    }

    override fun buildInputForLLM(
        state: ConversationState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>> {
        val branch = state.branches[state.currentBranchId] ?: error("No branch")
        val body = branch.messages

        val head = listOf(mf.msg("developer", systemInstruction))
        val tail = if (keepLastN <= 0) body else body.takeLast(keepLastN)

        return head + tail
    }
}