package agent.impl.openai.memory.engine

data class BuiltInput(
    val input: List<Map<String, Any>>
)

interface MemoryEngine {
    suspend fun onModeActivated(sessionId: String)
    suspend fun buildInput(sessionId: String, userText: String): BuiltInput
    suspend fun saveAssistantReply(sessionId: String, reply: String)
    suspend fun reset(sessionId: String)
}