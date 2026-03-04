package agent.impl.openai.context.sticky

import agent.impl.openai.context.ContextMode
import agent.impl.openai.context.ContextStrategy
import agent.impl.openai.messages.MessageFactory
import store.ConversationState

class StickyFactsStrategy(
    private val mf: MessageFactory,
    private val factsUpdater: FactsUpdater
) : ContextStrategy {

    override val mode = ContextMode.STICKY_FACTS

    override suspend fun onModeActivated(state: ConversationState): ConversationState {
        val branch = state.branches[state.currentBranchId] ?: return state
        val body = branch.messages

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
        val branch = state.branches[state.currentBranchId] ?: error("No branch")
        val body = branch.messages
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