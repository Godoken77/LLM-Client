package org.example.agent.impl.openai.messages


interface MessageFactory {
    fun msg(role: String, text: String): Map<String, Any>
    fun extractText(message: Map<String, Any>): String
}

class MessageFactoryImpl : MessageFactory {
    override fun msg(role: String, text: String): Map<String, Any> {
        val contentType = if (role == "assistant") "output_text" else "input_text"
        return mapOf(
            "role" to role,
            "content" to listOf(mapOf("type" to contentType, "text" to text))
        )
    }

    override fun extractText(message: Map<String, Any>): String {
        val content = message["content"] as? List<*> ?: return ""
        val first = content.firstOrNull() as? Map<*, *> ?: return ""
        return first["text"] as? String ?: ""
    }
}