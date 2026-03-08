package agent

import agent.impl.openai.agentImpl.AgentMemoryMode
import agent.impl.openai.memory.context.ContextMode
import agent.impl.openai.model.Answer

interface Agent {
    suspend fun ask(userText: String): Answer
    suspend fun reset()
    fun getContextMode(): ContextMode = ContextMode.SUMMARY
    fun setContextMode(newMode: ContextMode) = Unit
    fun getAgentMemoryMode(): AgentMemoryMode
    fun setAgentMemoryMode(newMode: AgentMemoryMode) = Unit
}

interface Answer