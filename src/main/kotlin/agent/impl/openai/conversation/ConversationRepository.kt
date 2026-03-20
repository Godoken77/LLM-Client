package agent.impl.openai.conversation

import store.ConversationState
import store.ConversationStore

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
