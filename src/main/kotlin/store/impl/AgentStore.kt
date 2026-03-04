package org.example.store.impl

import org.example.agent.Agent
import org.example.agent.impl.openai.OpenAIChatAgent
import org.example.agent.impl.openai.api.OpenaiApi
import org.example.store.AgentStore
import org.example.store.ConversationStore
import org.example.store.SessionId
import java.util.concurrent.ConcurrentHashMap

class AgentStore(
    private val conversationStore: ConversationStore,
    private val openaiApi: OpenaiApi
): AgentStore {
    private val agents = ConcurrentHashMap<String, Agent>()

    override fun getOrCreate(sessionId: SessionId): Agent {
        return agents.computeIfAbsent(sessionId) {
            OpenAIChatAgent(
                sessionId = sessionId,
                store = conversationStore,
                openaiApi = openaiApi
            )
        }
    }

    override fun remove(sessionId: SessionId) {
        agents.remove(sessionId)
    }
}