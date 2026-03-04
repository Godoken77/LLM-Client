package org.example.agent

import org.example.agent.impl.openai.model.Answer

interface Agent {
    fun ask(userText: String): Answer
    fun reset()
}

interface Answer