package agent.impl.openai.memory.ports

import store.ConversationState

interface MemoryConversationCompressor {
    suspend fun maybeCompress(state: ConversationState): ConversationState
}
