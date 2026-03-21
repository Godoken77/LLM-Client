package store

typealias Messages = MutableList<Map<String, Any>>

data class ConversationState(
    val summary: String = "",
    val messages: Messages = mutableListOf(),
    val facts: MutableMap<String, String> = mutableMapOf(),
)
