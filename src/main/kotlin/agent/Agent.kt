package agent

import agent.impl.openai.model.Answer

interface Agent {
    suspend fun ask(userText: String): Answer
    suspend fun reset()
}

interface Answer
