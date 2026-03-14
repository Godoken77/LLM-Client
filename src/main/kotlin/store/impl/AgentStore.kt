package store.impl

import agent.Agent
import agent.impl.openai.agentImpl.OpenAIChatAgent
import dependency.OpenaiDependency
import mcp.McpClient
import store.AgentStore
import store.SessionId
import java.util.concurrent.ConcurrentHashMap

class AgentStore(
    private val dependency: OpenaiDependency,
    private val sessionId: SessionId,
    private val mcpClient: McpClient? = null
): AgentStore {
    private val agents = ConcurrentHashMap<String, Agent>()

    override fun getOrCreate(): Agent {
        return agents.computeIfAbsent(sessionId) {
            OpenAIChatAgent(
                sessionId = sessionId,
                openai = dependency.openaiApi,
                parser = dependency.parser,
                contextEngine = dependency.contextEngine,
                layersEngine = dependency.layersEngine,
                invariantRepository = dependency.invariantRepository,
                invariantChecker = dependency.invariantChecker,
                refusalBuilder = dependency.invariantRefusalBuilder,
                mcpClient = mcpClient
            )
        }
    }

    override fun remove() {
        agents.remove(sessionId)
    }
}
