package agent

import agent.impl.openai.context.ContextMode
import agent.impl.openai.model.Answer

interface Agent {
    suspend fun ask(userText: String): Answer
    suspend fun reset()
    fun getContextMode(): ContextMode = ContextMode.SUMMARY
    fun setContextMode(newMode: ContextMode) = Unit
}

interface Answer