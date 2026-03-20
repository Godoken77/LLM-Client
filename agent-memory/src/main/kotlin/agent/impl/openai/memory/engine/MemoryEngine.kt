package agent.impl.openai.memory.engine

import agent.impl.openai.memory.context.ContextMode

interface MemoryEngine {
    suspend fun onModeActivated()
    suspend fun buildInput(userText: String): List<Map<String, Any>>
    suspend fun saveToolMessages(messages: List<Map<String, Any>>)
    suspend fun saveAssistantReply(reply: String)
    suspend fun reset()
    fun getContextMode(): ContextMode = error("Context mode is not supported by this engine")
    suspend fun setContextMode(newMode: ContextMode) {}
}
