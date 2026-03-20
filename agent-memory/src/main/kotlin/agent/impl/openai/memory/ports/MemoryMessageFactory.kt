package agent.impl.openai.memory.ports

interface MemoryMessageFactory {
    fun msg(role: String, text: String): Map<String, Any>
    fun extractText(message: Map<String, Any>): String
}
