package agent.impl.openai.memory.ports

data class MemoryLlmResponse(val status: Int, val body: String)

interface MemoryLlmClient {
    suspend fun responses(request: Map<String, Any>): MemoryLlmResponse
}
