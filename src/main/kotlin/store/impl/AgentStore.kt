package store.impl

import agent.Agent
import agent.impl.openai.agentImpl.OpenAIChatAgent
import agent.impl.openai.tools.ToolAwareOpenaiExecutor
import dependency.OpenaiDependency
import store.AgentStore
import store.SessionId
import java.util.concurrent.ConcurrentHashMap

class AgentStore(
    private val dependency: OpenaiDependency,
    private val sessionId: SessionId,
    private val executor: ToolAwareOpenaiExecutor
): AgentStore {
    private val agents = ConcurrentHashMap<String, Agent>()

    override fun getOrCreate(): Agent {
        return agents.computeIfAbsent(sessionId) {
            OpenAIChatAgent(
                sessionId = sessionId,
                openai = dependency.openaiApi,
                contextEngine = dependency.contextEngine,
                layersEngine = dependency.layersEngine,
                invariantRepository = dependency.invariantRepository,
                invariantChecker = dependency.invariantChecker,
                refusalBuilder = dependency.invariantRefusalBuilder,
                executor = executor
            )
        }
    }

    override fun remove() {
        agents.remove(sessionId)
    }
}
