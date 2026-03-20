package agent.impl.openai.memory.context.engine

import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.memory.context.ContextStrategy
import agent.impl.openai.memory.engine.MemoryEngine
import agent.impl.openai.memory.ports.MemoryContextPromptBuilder
import agent.impl.openai.memory.ports.MemoryConversationRepository
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.memory.ports.MemoryStateNormalizer

class ContextModeMemoryEngine(
    private val sessionId: String,
    private val conversationRepository: MemoryConversationRepository,
    private val normalizer: MemoryStateNormalizer,
    private val prompts: MemoryContextPromptBuilder,
    private val messageFactory: MemoryMessageFactory,
    private val strategies: Map<ContextMode, ContextStrategy>,
    private val systemInstruction: String,
    private var mode: ContextMode,
    private val keepLastN: Int = 12
) : MemoryEngine {

    override fun getContextMode(): ContextMode = mode

    override suspend fun setContextMode(newMode: ContextMode) {
        mode = newMode
        onModeActivated()
    }

    override suspend fun onModeActivated() {
        var state = conversationRepository.load(sessionId)
        val strategy = strategies[mode] ?: error("No strategy for mode=$mode")
        state = strategy.onModeActivated(state)
        conversationRepository.save(sessionId, state)
    }

    override suspend fun buildInput(userText: String): List<Map<String, Any>> {
        var state = conversationRepository.load(sessionId)
        state = normalizer.normalize(state)

        state = prompts.ensureDeveloper(state, systemInstruction)
        state = prompts.appendUser(state, userText)

        val strategy = strategies[mode] ?: error("No strategy for mode=$mode")
        state = strategy.onUserMessage(state)

        conversationRepository.save(sessionId, state)

        return strategy.buildInputForLLM(
            state = state,
            systemInstruction = systemInstruction,
            keepLastN = keepLastN
        )
    }

    override suspend fun saveToolMessages(messages: List<Map<String, Any>>) {
        if (messages.isEmpty()) return
        var state = conversationRepository.load(sessionId)
        val msgs = state.messages.toMutableList()
        msgs.addAll(messages)
        conversationRepository.save(sessionId, state.copy(messages = msgs))
    }

    override suspend fun saveAssistantReply(reply: String) {
        var state = conversationRepository.load(sessionId)
        val msgs = state.messages.toMutableList()
        msgs.add(messageFactory.msg("assistant", reply))
        state = state.copy(messages = msgs)
        conversationRepository.save(sessionId, state)
    }

    override suspend fun reset() {
        conversationRepository.delete(sessionId)
    }
}
