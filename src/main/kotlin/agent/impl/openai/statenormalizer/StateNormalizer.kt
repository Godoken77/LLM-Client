package org.example.agent.impl.openai.statenormalizer

import org.example.agent.impl.openai.messages.MessageFactory
import org.example.store.ConversationState

interface StateNormalizer {
    fun normalize(state: ConversationState): ConversationState
}

class StateNormalizerImpl(
    private val messages: MessageFactory
) : StateNormalizer {

    override fun normalize(state: ConversationState): ConversationState {
        val msgs = state.messages.toMutableList()

        for (i in msgs.indices) {
            val role = msgs[i]["role"] as? String ?: continue
            val text = messages.extractText(msgs[i])

            val desiredType = when (role) {
                "assistant" -> "output_text"
                "user", "developer", "system" -> "input_text"
                else -> continue
            }

            val content = msgs[i]["content"] as? List<*> ?: continue
            val first = content.firstOrNull() as? Map<*, *> ?: continue
            val t = first["type"] as? String ?: ""

            if (role == "assistant" && t == "refusal") continue

            if (t != desiredType) {
                msgs[i] = mapOf(
                    "role" to role,
                    "content" to listOf(mapOf("type" to desiredType, "text" to text))
                )
            }
        }

        return state.copy(messages = msgs)
    }
}