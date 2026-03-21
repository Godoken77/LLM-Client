package agent.impl.openai.taskstages.adapters

import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.taskstages.ports.TaskLlmClient
import agent.impl.openai.taskstages.ports.TaskLlmResponse
import agent.impl.openai.taskstages.ports.TaskMessageFactory

class TaskLlmClientAdapter(private val api: OpenaiApi) : TaskLlmClient {
    override suspend fun responses(request: Map<String, Any>): TaskLlmResponse {
        val r = api.responses(request)
        return TaskLlmResponse(status = r.status, body = r.body)
    }
}

class TaskMessageFactoryAdapter(private val mf: MessageFactory) : TaskMessageFactory {
    override fun msg(role: String, text: String): Map<String, Any> = mf.msg(role, text)
    override fun extractText(message: Map<String, Any>): String = mf.extractText(message)
}
