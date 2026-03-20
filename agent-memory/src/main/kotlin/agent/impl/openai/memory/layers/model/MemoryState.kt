package agent.impl.openai.memory.layers.model

import agent.impl.openai.taskstages.TaskStateMachine

data class MemoryState(
    val shortTerm: ShortTermMemory = ShortTermMemory(),
    val working: WorkingMemory = WorkingMemory(),
    val longTerm: LongTermMemory = LongTermMemory()
)

data class ShortTermMemory(
    val messages: MutableList<Map<String, Any>> = mutableListOf()
)

data class WorkingMemory(
    val summary: String = "",
    val stateMachine: TaskStateMachine = TaskStateMachine()
)

data class LongTermMemory(
    val facts: MutableMap<String, String> = mutableMapOf(),
    val decisions: MutableList<String> = mutableListOf(),
    val knowledge: MutableList<String> = mutableListOf()
)
