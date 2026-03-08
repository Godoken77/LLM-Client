package agent.impl.openai.memory.context.sticky

import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.memory.context.ContextStrategy
import agent.impl.openai.messages.MessageFactory
import store.ConversationState

class StickyFactsStrategy(
    private val mf: MessageFactory,
    private val factsUpdater: FactsUpdater
) : ContextStrategy {

    override val mode = ContextMode.STICKY_FACTS

    override suspend fun onModeActivated(state: ConversationState): ConversationState {
        val body = state.messages.filter { m ->
            val role = m["role"] as? String
            role != "developer" && role != "system"
        }

        val lastUser = body.lastOrNull { (it["role"] as? String) == "user" }
        val lastUserText = lastUser?.let { mf.extractText(it) }.orEmpty()

        val recentDialogWithSummary =
            if (state.summary.isNotBlank()) {
                listOf(mf.msg("developer", "SUMMARY (сжатый контекст):\n${state.summary}")) + body
            } else {
                body
            }

        val updated = factsUpdater.updateFacts(
            existingFacts = state.facts,
            lastUserMessage = lastUserText,
            recentDialog = recentDialogWithSummary
        )

        return state.copy(facts = updated.toMutableMap())
    }

    override suspend fun onUserMessage(state: ConversationState): ConversationState {
        return onModeActivated(state)
    }

    override fun buildInputForLLM(
        state: ConversationState,
        systemInstruction: String,
        keepLastN: Int
    ): List<Map<String, Any>> {
        val body = state.messages.filter { m ->
            val role = m["role"] as? String
            role != "developer" && role != "system"
        }

        val tail = if (keepLastN <= 0) body else body.takeLast(keepLastN)

        val factsText = if (state.facts.isEmpty()) {
            "facts: (пока пусто)"
        } else {
            state.facts.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        }

        return listOf(
            mf.msg("developer", systemInstruction),
            mf.msg("developer", "Важные факты (facts):\n$factsText")
        ) + tail
    }
}