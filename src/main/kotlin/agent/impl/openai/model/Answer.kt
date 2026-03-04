package org.example.agent.impl.openai.model

import org.example.agent.Answer

data class Answer(val reply: String, val stats: TokenStats?): Answer

data class TokenStats(
    val userTokens: Int,        // токены только текущего userText
    val historyTokens: Int,      // токены всей истории + текущий userText (то, что реально ушло в запрос)
    val responseTokens: Int,     // output_tokens модели
    val reasoningTokens: Int?,   // если вернётся
    val totalTokens: Int?        // если вернётся
)