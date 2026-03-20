package agent.impl.openai.memory.context.summary

import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.memory.context.ContextStrategy
import agent.impl.openai.memory.ports.MemoryConversationCompressor
import agent.impl.openai.memory.ports.MemoryContextPromptBuilder
import store.ConversationState

class SummaryStrategy(
    private val compressor: MemoryConversationCompressor,
    private val prompts: MemoryContextPromptBuilder
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
