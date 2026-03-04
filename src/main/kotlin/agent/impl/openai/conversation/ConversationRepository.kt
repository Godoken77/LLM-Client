package org.example.agent.impl.openai.conversation

import org.example.store.ConversationState
import org.example.store.ConversationStore

interface ConversationRepository {
    fun load(sessionId: String): ConversationState
    fun save(sessionId: String, state: ConversationState)
    fun delete(sessionId: String)
}

class ConversationRepositoryImpl(
    private val conversationStore: ConversationStore
) : ConversationRepository {
    override fun load(sessionId: String): ConversationState {
        return conversationStore.loadState(sessionId)
    }

    override fun save(sessionId: String, state: ConversationState) {
        conversationStore.saveState(sessionId, state)
    }

    override fun delete(sessionId: String) {
        conversationStore.delete(sessionId)
    }
}