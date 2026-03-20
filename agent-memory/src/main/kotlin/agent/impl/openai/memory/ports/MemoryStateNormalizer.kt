package agent.impl.openai.memory.ports

import store.ConversationState

interface MemoryStateNormalizer {
    fun normalize(state: ConversationState): ConversationState
}
