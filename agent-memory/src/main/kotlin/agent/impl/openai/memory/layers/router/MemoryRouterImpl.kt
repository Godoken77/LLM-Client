package agent.impl.openai.memory.layers.router

import agent.impl.openai.memory.layers.model.MemoryState
import agent.impl.openai.memory.layers.updater.longterm.LongTermMemoryUpdater
import agent.impl.openai.memory.layers.updater.workterm.WorkingMemoryUpdater
import agent.impl.openai.memory.ports.MemoryMessageFactory

class MemoryRouterImpl(
    private val messageFactory: MemoryMessageFactory,
    private val workingMemoryUpdater: WorkingMemoryUpdater,
    private val longTermMemoryUpdater: LongTermMemoryUpdater
) : MemoryRouter {

    override suspend fun updateAfterUserMessage(
        memory: MemoryState,
        userText: String
    ): MemoryState {
        val short = memory.shortTerm.messages.toMutableList()
        short.add(messageFactory.msg("user", userText))

        var updated = memory.copy(
            shortTerm = memory.shortTerm.copy(messages = short)
        )

        updated = updated.copy(
            working = workingMemoryUpdater.update(updated.working, updated.shortTerm.messages),
            longTerm = longTermMemoryUpdater.update(updated.longTerm, updated.shortTerm.messages)
        )

        return updated
    }

    override suspend fun updateAfterAssistantMessage(
        memory: MemoryState,
        assistantText: String
    ): MemoryState {
        val short = memory.shortTerm.messages.toMutableList()
        short.add(messageFactory.msg("assistant", assistantText))

        return memory.copy(
            shortTerm = memory.shortTerm.copy(messages = short)
        )
    }
}
