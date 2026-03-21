package agent.impl.openai.taskstages.ports

data class TaskLlmResponse(val status: Int, val body: String)

interface TaskLlmClient {
    suspend fun responses(request: Map<String, Any>): TaskLlmResponse
}
