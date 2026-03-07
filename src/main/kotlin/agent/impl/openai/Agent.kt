package agent.impl.openai

import kotlinx.coroutines.runBlocking
import agent.Agent
import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.context.ContextMode
import agent.impl.openai.context.ContextStrategy
import agent.impl.openai.conversation.ConversationRepository
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.model.Answer
import agent.impl.openai.model.ModelInstruction
import agent.impl.openai.model.ModelReasoningEffort
import agent.impl.openai.model.ModelVersion
import agent.impl.openai.prompt.PromptBuilder
import agent.impl.openai.responseparser.ResponseParser
import agent.impl.openai.statenormalizer.StateNormalizer
import kotlinx.coroutines.sync.withLock
import session.SessionLocks

class OpenAIChatAgent(
    private val sessionId: String,
    private val conversationRepository: ConversationRepository,
    private val normalizer: StateNormalizer,
    private val prompts: PromptBuilder,
    private val openai: OpenaiApi,
    private val parser: ResponseParser,
    private val messageFactory: MessageFactory,
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION,
    private val reasoningEffort: ModelReasoningEffort = ModelReasoningEffort.DEFAULT_REASONING_EFFORT,
    private val systemInstruction: ModelInstruction = ModelInstruction.DEFAULT_SYSTEM_INSTRUCTION,
    private val strategies: Map<ContextMode, ContextStrategy>,
    private var mode: ContextMode = ContextMode.SUMMARY
) : Agent {

    private companion object {
        const val KEEP_LAST_MESSAGES_COUNT = 12
    }

    private val mutex = SessionLocks.mutexFor(sessionId)

    override fun setContextMode(newMode: ContextMode) = runBlocking {
        mutex.withLock {
            mode = newMode

            var state = conversationRepository.load(sessionId)
            val strategy = strategies[mode] ?: error("No strategy for mode=$mode")
            state = strategy.onModeActivated(state)

            conversationRepository.save(sessionId, state)
        }
    }

    override suspend fun ask(userText: String): Answer = mutex.withLock {
        var state = conversationRepository.load(sessionId)
        state = normalizer.normalize(state)

        state = prompts.ensureDeveloper(state, systemInstruction.instruction)
        state = prompts.appendUser(state, userText)

        val strategy = strategies[mode] ?: error("No strategy for mode=$mode")

        state = strategy.onUserMessage(state)

        val inputForLLM = strategy.buildInputForLLM(
            state = state,
            systemInstruction = systemInstruction.instruction,
            keepLastN = KEEP_LAST_MESSAGES_COUNT
        )

        val historyTokens = openai.inputTokens(model.version, inputForLLM)

        val requestMap: Map<String, Any> = mapOf(
            "model" to model.version,
            "input" to inputForLLM,
            "reasoning" to mapOf("effort" to reasoningEffort.level, "summary" to "concise")
        )

        val raw = openai.responses(requestMap)
        val parsed = parser.parse(raw)

        val msgs = state.messages.toMutableList()
        msgs.add(messageFactory.msg("assistant", parsed.reply))
        state = state.copy(messages = msgs)

        conversationRepository.save(sessionId, state)

        val stats = parsed.stats?.copy(historyTokens = historyTokens)
        Answer(parsed.reply, stats)
    }

    override suspend fun reset() = mutex.withLock {
        conversationRepository.delete(sessionId)
    }

    override fun getContextMode(): ContextMode = mode
}