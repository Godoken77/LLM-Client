package agent.impl.openai.conversation

import store.ConversationState

interface ConversationRepository {
    fun load(sessionId: String): ConversationState
    fun save(sessionId: String, state: ConversationState)
    fun delete(sessionId: String)
}
