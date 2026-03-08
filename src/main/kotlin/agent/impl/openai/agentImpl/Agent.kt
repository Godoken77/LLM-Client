package agent.impl.openai.agentImpl

import kotlinx.coroutines.runBlocking
import agent.Agent
import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.invariants.invariantchecker.InvariantChecker
import agent.impl.openai.invariants.refusalbuilder.InvariantRefusalBuilder
import agent.impl.openai.invariants.repository.InvariantRepository
import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.memory.context.engine.ContextModeMemoryEngine
import agent.impl.openai.memory.engine.MemoryEngine
import agent.impl.openai.memory.layers.engine.MemoryLayersEngine
import agent.impl.openai.model.Answer
import agent.impl.openai.model.ModelReasoningEffort
import agent.impl.openai.model.ModelVersion
import agent.impl.openai.responseparser.ResponseParser
import kotlinx.coroutines.sync.withLock
import session.SessionLocks

enum class AgentMemoryMode {
    CONTEXT_MODE,
    MEMORY_LAYERS
}

class OpenAIChatAgent(
    private val sessionId: String,
    private val openai: OpenaiApi,
    private val parser: ResponseParser,
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION,
    private val reasoningEffort: ModelReasoningEffort = ModelReasoningEffort.DEFAULT_REASONING_EFFORT,
    private var memoryMode: AgentMemoryMode = AgentMemoryMode.MEMORY_LAYERS,
    private val contextEngine: ContextModeMemoryEngine,
    private val layersEngine: MemoryLayersEngine,
    private val invariantRepository: InvariantRepository,
    private val invariantChecker: InvariantChecker,
    private val refusalBuilder: InvariantRefusalBuilder
) : Agent {

    private val mutex = SessionLocks.mutexFor(sessionId)

    private fun currentEngine(): MemoryEngine {
        return when (memoryMode) {
            AgentMemoryMode.CONTEXT_MODE -> contextEngine
            AgentMemoryMode.MEMORY_LAYERS -> layersEngine
        }
    }

    override fun setAgentMemoryMode(newMode: AgentMemoryMode) = runBlocking {
        mutex.withLock {
            memoryMode = newMode
            currentEngine().onModeActivated(sessionId)
        }
    }

    override fun getAgentMemoryMode(): AgentMemoryMode = memoryMode

    override fun setContextMode(newMode: ContextMode) = runBlocking {
        mutex.withLock {
            contextEngine.setContextMode(newMode)
        }
    }

    override fun getContextMode(): ContextMode = contextEngine.getContextMode()

    override suspend fun ask(userText: String): Answer = mutex.withLock {
        val invariants = invariantRepository.load(sessionId)

        val requestCheck = invariantChecker.checkUserRequest(invariants, userText)
        if (!requestCheck.allowed) {
            val refusal = refusalBuilder.buildRefusal(userText, requestCheck)
            return@withLock Answer(refusal, null)
        }

        val engine = currentEngine()
        val built = engine.buildInput(sessionId, userText)

        val historyTokens = openai.inputTokens(model.version, built.input)

        val requestMap: Map<String, Any> = mapOf(
            "model" to model.version,
            "input" to built.input,
            "reasoning" to mapOf(
                "effort" to reasoningEffort.level,
                "summary" to "concise"
            )
        )

        val raw = openai.responses(requestMap)
        val parsed = parser.parse(raw)

        val replyCheck = invariantChecker.checkAssistantReply(invariants, parsed.reply)
        val finalReply = if (replyCheck.allowed) {
            parsed.reply
        } else {
            refusalBuilder.buildRefusal(userText, replyCheck)
        }

        engine.saveAssistantReply(sessionId, finalReply)

        val stats = parsed.stats?.copy(historyTokens = historyTokens)
        Answer(finalReply, stats)
    }

    override suspend fun reset() = mutex.withLock {
        contextEngine.reset(sessionId)
        layersEngine.reset(sessionId)
    }
}