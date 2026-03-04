package agent.impl.openai.context.branching

import agent.impl.openai.context.ContextMode
import agent.impl.openai.context.ContextStrategy
import agent.impl.openai.messages.MessageFactory
import store.ConversationState

class BranchingStrategy(
    private val mf: MessageFactory
) : ContextStrategy {

    override val mode = ContextMode.BRANCHING

    override suspend fun onUserMessage(state: ConversationState): ConversationState {
        return state
    }

    override fun buildInputForLLM(
        state: ConversationState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>> {
        val branch = state.branches[state.currentBranchId] ?: error("No branch")
        val tail = if (keepLastN <= 0) branch.messages else branch.messages.takeLast(keepLastN)
        return listOf(mf.msg("developer", systemInstruction)) + tail
    }
}