package agent.impl.openai.memory.layers.router

import agent.impl.openai.memory.layers.model.MemoryState

interface MemoryRouter {
    suspend fun updateAfterUserMessage(
        memory: MemoryState,
        userText: String
    ): MemoryState

    suspend fun updateAfterAssistantMessage(
        memory: MemoryState,
        assistantText: String
    ): MemoryState
}
