package model

import agent.impl.openai.model.TokenStats

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

data class ContextInfoResponse(
    val mode: String,
    val available: List<String>
)

data class SetContextRequest(
    val mode: String
)

data class SetContextResponse(
    val ok: Boolean,
    val mode: String,
    val message: String? = null
)