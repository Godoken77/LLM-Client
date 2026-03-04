package org.example.model

import org.example.agent.impl.openai.model.TokenStats

// ------------------- API модели запрос/ответ -------------------
data class ChatRequest(
    val message: String
)

data class ChatResponse(
    val reply: String,
    val stats: TokenStats? = null
)

data class ResetResponse(
    val ok: Boolean
)

data class HistoryItem(
    val role: String,
    val text: String
)

data class HistoryResponse(
    val items: List<HistoryItem>
)