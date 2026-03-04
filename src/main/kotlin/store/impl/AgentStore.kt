package org.example.store.impl

import org.example.agent.Agent
import org.example.agent.impl.openai.OpenAIChatAgent
import org.example.dependency.OpenaiDependency
import org.example.store.AgentStore
import org.example.store.SessionId
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
                compressor = dependency.compressor,
                openai = dependency.openaiApi,
                parser = dependency.parser
            )
        }
    }

    override fun remove(sessionId: SessionId) {
        agents.remove(sessionId)
    }
}