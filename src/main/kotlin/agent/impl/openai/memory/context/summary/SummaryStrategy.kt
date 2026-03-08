package agent.impl.openai.memory.context.summary

import agent.impl.openai.compsessor.ConversationCompressor
import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.memory.context.ContextStrategy
import agent.impl.openai.prompt.PromptBuilder
import store.ConversationState

class SummaryStrategy(
    private val compressor: ConversationCompressor,
    private val prompts: PromptBuilder
) : ContextStrategy {

    override val mode: ContextMode = ContextMode.SUMMARY

    override suspend fun onUserMessage(state: ConversationState): ConversationState {
        return compressor.maybeCompress(state)
    }

    override fun buildInputForLLM(
        state: ConversationState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>> {
        var s = state
        s = prompts.ensureDeveloper(s, systemInstruction)
        return prompts.buildInput(s)
    }
}