package store.impl

import agent.Agent
import agent.impl.openai.agentImpl.OpenAIChatAgent
import dependency.OpenaiDependency
import store.AgentStore
import store.SessionId
import java.util.concurrent.ConcurrentHashMap

class AgentStore(
    private val dependency: OpenaiDependency,
    private val sessionId: SessionId
): AgentStore {
    private val agents = ConcurrentHashMap<String, Agent>()

    override fun getOrCreate(): Agent {
        return agents.computeIfAbsent(sessionId) {
            OpenAIChatAgent(
                sessionId = sessionId,
                openai = dependency.openaiApi,
                parser = dependency.parser,
                contextEngine = dependency.contextEngine,
                layersEngine = dependency.layersEngine
            )
        }
    }

    override fun remove() {
        agents.remove(sessionId)
    }
}