package agent.impl.openai.prompt

import agent.impl.openai.messages.MessageFactory
import store.ConversationState

interface PromptBuilder {
    fun ensureDeveloper(state: ConversationState, systemInstruction: String): ConversationState
    fun appendUser(state: ConversationState, userText: String): ConversationState
    fun buildInput(state: ConversationState): List<Map<String, Any>>
}

class PromptBuilderImpl(
    private val messages: MessageFactory
) : PromptBuilder {

    override fun ensureDeveloper(state: ConversationState, systemInstruction: String): ConversationState {
        val msgs = state.messages.toMutableList()
        if (msgs.isEmpty()) {
            msgs.add(messages.msg("developer", systemInstruction))
            return state.copy(messages = msgs)
        }

        val firstRole = msgs.firstOrNull()?.get("role") as? String
        if (firstRole != "developer" && firstRole != "system") {
            msgs.add(0, messages.msg("developer", systemInstruction))
        }
        return state.copy(messages = msgs)
    }

    override fun appendUser(state: ConversationState, userText: String): ConversationState {
        val msgs = state.messages.toMutableList()
        msgs.add(messages.msg("user", userText))
        return state.copy(messages = msgs)
    }

    override fun buildInput(state: ConversationState): List<Map<String, Any>> {
        val msgs = state.messages.toMutableList()

        if (state.summary.isNotBlank()) {
            val summaryMsg = messages.msg(
                "developer",
                "Сжатый контекст предыдущего диалога (summary):\n${state.summary}"
            )
            if (msgs.isNotEmpty()) msgs.add(1, summaryMsg) else msgs.add(summaryMsg)
        }
        return msgs
    }
}