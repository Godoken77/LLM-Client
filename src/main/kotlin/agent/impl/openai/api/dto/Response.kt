package agent.impl.openai.api.dto

data class Response(
    val status: Int,
    val statusText: String,
    val body: String
)

data class AgentMemoryModeResponse(
    val mode: String,
    val available: List<String>
)

data class SetAgentMemoryModeRequest(
    val mode: String
)

data class SetAgentMemoryModeResponse(
    val ok: Boolean,
    val mode: String,
    val message: String? = null
)