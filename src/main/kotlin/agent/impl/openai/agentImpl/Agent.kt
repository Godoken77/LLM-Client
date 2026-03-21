package agent.impl.openai.agentImpl

import kotlinx.coroutines.runBlocking
import agent.Agent
import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.invariants.invariantchecker.InvariantChecker
import agent.impl.openai.invariants.refusalbuilder.InvariantRefusalBuilder
import agent.impl.openai.invariants.repository.InvariantRepository
import agent.impl.openai.memory.engine.MemoryEngine
import agent.impl.openai.model.Answer
import agent.impl.openai.model.ModelReasoningEffort
import agent.impl.openai.model.ModelVersion
import agent.impl.openai.tools.ToolAwareOpenaiExecutor
import kotlinx.coroutines.sync.withLock
import session.SessionLocks

class OpenAIChatAgent(
    private val sessionId: String,
    private val openai: OpenaiApi,
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION,
    private val reasoningEffort: ModelReasoningEffort = ModelReasoningEffort.DEFAULT_REASONING_EFFORT,
    private val engine: MemoryEngine,
    private val invariantRepository: InvariantRepository,
    private val invariantChecker: InvariantChecker,
    private val refusalBuilder: InvariantRefusalBuilder,
    private val executor: ToolAwareOpenaiExecutor
) : Agent {

    private val mutex = SessionLocks.mutexFor(sessionId)

    override suspend fun ask(userText: String): Answer = mutex.withLock {
        val invariants = invariantRepository.load(sessionId)

        val requestCheck = invariantChecker.checkUserRequest(invariants, userText)
        if (!requestCheck.allowed) {
            return@withLock Answer(refusalBuilder.buildRefusal(userText, requestCheck), null)
        }

        val input = engine.buildInput(userText)
        val historyTokens = openai.inputTokens(model.version, input)

        val parsed = executor.execute(model.version, reasoningEffort.level, input)

        val replyCheck = invariantChecker.checkAssistantReply(invariants, parsed.reply)
        val finalReply = if (replyCheck.allowed) {
            parsed.reply
        } else {
            refusalBuilder.buildRefusal(userText, replyCheck)
        }

        if (replyCheck.allowed) engine.saveToolMessages(parsed.toolMessages)
        engine.saveAssistantReply(finalReply)

        Answer(finalReply, parsed.stats?.copy(historyTokens = historyTokens))
    }

    override suspend fun reset() = mutex.withLock {
        engine.reset()
    }
}
