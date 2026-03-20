package agent.impl.openai.memory.ports

import store.ConversationState

interface MemoryContextPromptBuilder {
    fun ensureDeveloper(state: ConversationState, systemInstruction: String): ConversationState
    fun appendUser(state: ConversationState, userText: String): ConversationState
    fun buildInput(state: ConversationState): List<Map<String, Any>>
}
