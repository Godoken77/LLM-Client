package agent.impl.openai.memory.engine

interface MemoryEngine {
    suspend fun onModeActivated()
    suspend fun buildInput(userText: String): List<Map<String, Any>>
    suspend fun saveToolMessages(messages: List<Map<String, Any>>)
    suspend fun saveAssistantReply(reply: String)
    suspend fun reset()
}
