package org.example.model

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

data class TokenStats(
    val userTokens: Int,        // токены только текущего userText
    val historyTokens: Int,      // токены всей истории + текущий userText (то, что реально ушло в запрос)
    val responseTokens: Int,     // output_tokens модели
    val reasoningTokens: Int?,   // если вернётся
    val totalTokens: Int?        // если вернётся
)