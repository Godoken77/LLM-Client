package agent.impl.openai.memory.context

import store.ConversationState

enum class ContextMode {
    SLIDING_WINDOW,
    STICKY_FACTS,
    SUMMARY
}

interface ContextStrategy {
    val mode: ContextMode

    suspend fun onUserMessage(state: ConversationState): ConversationState

    fun buildInputForLLM(
        state: ConversationState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>>

    suspend fun onModeActivated(state: ConversationState): ConversationState = state
}
