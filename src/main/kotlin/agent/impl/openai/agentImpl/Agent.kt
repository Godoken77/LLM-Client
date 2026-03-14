package agent.impl.openai.agentImpl

import kotlinx.coroutines.runBlocking
import agent.Agent
import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.api.dto.Response
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
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.sync.withLock
import mcp.McpClient
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
    private val refusalBuilder: InvariantRefusalBuilder,
    private val mcpClient: McpClient? = null
) : Agent {

    private val mutex = SessionLocks.mutexFor(sessionId)
    private val gson = Gson()

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

        val tools = mcpClient?.listTools()?.map { tool ->
            buildMap<String, Any> {
                put("type", "function")
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.inputSchema ?: mapOf("type" to "object"))
            }
        } ?: emptyList()

        val input = built.input.toMutableList<Map<String, Any>>()
        var raw: Response
        var parsed: Answer

        while (true) {
            val requestMap = mutableMapOf<String, Any>(
                "model" to model.version,
                "input" to input,
                "reasoning" to mapOf("effort" to reasoningEffort.level, "summary" to "concise")
            )
            if (tools.isNotEmpty()) requestMap["tools"] = tools

            raw = openai.responses(requestMap)

            val functionCalls = extractFunctionCalls(raw)
            if (functionCalls.isEmpty()) {
                parsed = parser.parse(raw)
                break
            }

            // Append function_call items so the model has context of what it called
            for (call in functionCalls) {
                input.add(mapOf(
                    "type" to "function_call",
                    "call_id" to call.callId,
                    "name" to call.name,
                    "arguments" to call.argumentsJson
                ))
            }

            // Execute each tool call and append results
            for (call in functionCalls) {
                @Suppress("UNCHECKED_CAST")
                val args: Map<String, Any> = runCatching {
                    gson.fromJson(call.argumentsJson, Map::class.java) as Map<String, Any>
                }.getOrDefault(emptyMap())

                val result = mcpClient!!.callTool(call.name, args)

                input.add(mapOf(
                    "type" to "function_call_output",
                    "call_id" to call.callId,
                    "output" to result.content
                ))
            }
        }

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

    private data class FunctionCall(val callId: String, val name: String, val argumentsJson: String)

    private fun extractFunctionCalls(raw: Response): List<FunctionCall> {
        if (raw.body.isBlank() || raw.status !in 200..299) return emptyList()
        return runCatching {
            val root = gson.fromJson(raw.body, JsonObject::class.java)
            val output = root.getAsJsonArray("output") ?: return emptyList()
            output.mapNotNull { item ->
                val obj = item.asJsonObject
                if (obj.get("type")?.asString != "function_call") return@mapNotNull null
                val callId = obj.get("call_id")?.asString ?: return@mapNotNull null
                val name = obj.get("name")?.asString ?: return@mapNotNull null
                val arguments = obj.get("arguments")?.asString ?: "{}"
                FunctionCall(callId, name, arguments)
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun reset() = mutex.withLock {
        contextEngine.reset(sessionId)
        layersEngine.reset(sessionId)
    }
}
