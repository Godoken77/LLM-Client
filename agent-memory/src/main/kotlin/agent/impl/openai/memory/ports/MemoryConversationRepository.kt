package agent.impl.openai.memory.ports

import store.ConversationState

interface MemoryConversationRepository {
    fun load(sessionId: String): ConversationState
    fun save(sessionId: String, state: ConversationState)
    fun delete(sessionId: String)
}
