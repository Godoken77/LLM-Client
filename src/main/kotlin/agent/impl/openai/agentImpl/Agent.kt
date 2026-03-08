package agent.impl.openai.agentImpl

import kotlinx.coroutines.runBlocking
import agent.Agent
import agent.impl.openai.api.OpenaiApi
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
    private var memoryMode: AgentMemoryMode = AgentMemoryMode.CONTEXT_MODE,
    private val contextEngine: ContextModeMemoryEngine,
    private val layersEngine: MemoryLayersEngine
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

        engine.saveAssistantReply(sessionId, parsed.reply)

        val stats = parsed.stats?.copy(historyTokens = historyTokens)
        return@withLock Answer(parsed.reply, stats)
    }

    override suspend fun reset() = mutex.withLock {
        contextEngine.reset(sessionId)
        layersEngine.reset(sessionId)
    }
}