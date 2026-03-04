package store.impl

import agent.Agent
import agent.impl.openai.OpenAIChatAgent
import dependency.OpenaiDependency
import store.AgentStore
import store.SessionId
import java.util.concurrent.ConcurrentHashMap

class AgentStore(
    private val dependency: OpenaiDependency
): AgentStore {
    private val agents = ConcurrentHashMap<String, Agent>()

    override fun getOrCreate(sessionId: SessionId): Agent {
        return agents.computeIfAbsent(sessionId) {
            OpenAIChatAgent(
                sessionId = sessionId,
                messageFactory = dependency.msgFactory,
                conversationRepository = dependency.conversationRepository,
                normalizer = dependency.normalizer,
                prompts = dependency.prompts,
                openai = dependency.openaiApi,
                parser = dependency.parser,
                strategies = dependency.strategies
            )
        }
    }

    override fun remove(sessionId: SessionId) {
        agents.remove(sessionId)
    }
}