package org.example.agent.impl.openai

import kotlinx.coroutines.runBlocking
import org.example.agent.Agent
import org.example.agent.impl.openai.api.OpenaiApi
import org.example.agent.impl.openai.compsessor.ConversationCompressor
import org.example.agent.impl.openai.conversation.ConversationRepository
import org.example.agent.impl.openai.messages.MessageFactory
import org.example.agent.impl.openai.model.Answer
import org.example.agent.impl.openai.model.ModelInstruction
import org.example.agent.impl.openai.model.ModelReasoningEffort
import org.example.agent.impl.openai.model.ModelVersion
import org.example.agent.impl.openai.prompt.PromptBuilder
import org.example.agent.impl.openai.responseparser.ResponseParser
import org.example.agent.impl.openai.statenormalizer.StateNormalizer
import org.example.session.SessionLocks

class OpenAIChatAgent(
    private val sessionId: String,
    private val conversationRepository: ConversationRepository,
    private val normalizer: StateNormalizer,
    private val prompts: PromptBuilder,
    private val compressor: ConversationCompressor,
    private val openai: OpenaiApi,
    private val parser: ResponseParser,
    private val messageFactory: MessageFactory,
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION,
    private val reasoningEffort: ModelReasoningEffort = ModelReasoningEffort.DEFAULT_REASONING_EFFORT,
    private val systemInstruction: ModelInstruction = ModelInstruction.DEFAULT_SYSTEM_INSTRUCTION
) : Agent {

    private val lock = SessionLocks.lockFor(sessionId)

    override fun ask(userText: String): Answer = synchronized(lock) {
        runBlocking {
            var state = conversationRepository.load(sessionId)
            state = normalizer.normalize(state)

            state = prompts.ensureDeveloper(state, systemInstruction.instruction)
            state = prompts.appendUser(state, userText)

            state = compressor.maybeCompress(state)

            val inputForLLM = prompts.buildInput(state)

            val historyTokens: Int = openai.inputTokens(model.version, inputForLLM)

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
    }

    override fun reset() = synchronized(lock) {
        conversationRepository.delete(sessionId)
    }
}