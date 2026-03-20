package agent.impl.openai.memory.context.engine

import agent.impl.openai.conversation.ConversationRepository
import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.memory.context.ContextStrategy
import agent.impl.openai.memory.engine.BuiltInput
import agent.impl.openai.memory.engine.MemoryEngine
import agent.impl.openai.memory.ports.MemoryContextPromptBuilder
import agent.impl.openai.memory.ports.MemoryMessageFactory
import agent.impl.openai.memory.ports.MemoryStateNormalizer
import agent.impl.openai.model.ModelInstruction

class ContextModeMemoryEngine(
    private val sessionId: String,
    private val conversationRepository: ConversationRepository,
    private val normalizer: MemoryStateNormalizer,
    private val prompts: MemoryContextPromptBuilder,
    private val messageFactory: MemoryMessageFactory,
    private val strategies: Map<ContextMode, ContextStrategy>,
    private val systemInstruction: ModelInstruction,
    private var mode: ContextMode,
    private val keepLastN: Int = 12
) : MemoryEngine {

    fun getContextMode(): ContextMode = mode

    suspend fun setContextMode(newMode: ContextMode) {
        mode = newMode
        onModeActivated(sessionId)
    }

    override suspend fun onModeActivated(sessionId: String) {
        var state = conversationRepository.load(sessionId)
        val strategy = strategies[mode] ?: error("No strategy for mode=$mode")
        state = strategy.onModeActivated(state)
        conversationRepository.save(sessionId, state)
    }

    override suspend fun buildInput(sessionId: String, userText: String): BuiltInput {
        var state = conversationRepository.load(sessionId)
        state = normalizer.normalize(state)

        state = prompts.ensureDeveloper(state, systemInstruction.instruction)
        state = prompts.appendUser(state, userText)

        val strategy = strategies[mode] ?: error("No strategy for mode=$mode")
        state = strategy.onUserMessage(state)

        conversationRepository.save(sessionId, state)

        val input = strategy.buildInputForLLM(
            state = state,
            systemInstruction = systemInstruction.instruction,
            keepLastN = keepLastN
        )

        return BuiltInput(input = input)
    }

    override suspend fun saveToolMessages(sessionId: String, messages: List<Map<String, Any>>) {
        if (messages.isEmpty()) return
        var state = conversationRepository.load(sessionId)
        val msgs = state.messages.toMutableList()
        msgs.addAll(messages)
        conversationRepository.save(sessionId, state.copy(messages = msgs))
    }

    override suspend fun saveAssistantReply(sessionId: String, reply: String) {
        var state = conversationRepository.load(sessionId)
        val msgs = state.messages.toMutableList()
        msgs.add(messageFactory.msg("assistant", reply))
        state = state.copy(messages = msgs)
        conversationRepository.save(sessionId, state)
    }

    override suspend fun reset(sessionId: String) {
        conversationRepository.delete(sessionId)
    }
}
