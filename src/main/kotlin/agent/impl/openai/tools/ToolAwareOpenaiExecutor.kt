package agent.impl.openai.tools

import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.model.Answer
import agent.impl.openai.responseparser.ResponseParser

interface ToolAwareOpenaiExecutor {
    suspend fun execute(
        model: String,
        reasoningEffort: String,
        input: List<Map<String, Any>>
    ): Answer
}

class ToolAwareOpenaiExecutorImpl(
    private val openai: OpenaiApi,
    private val parser: ResponseParser,
    private val toolProvider: McpToolProvider? = null
) : ToolAwareOpenaiExecutor {

    override suspend fun execute(
        model: String,
        reasoningEffort: String,
        input: List<Map<String, Any>>
    ): Answer {
        val tools = toolProvider?.getToolDefinitions() ?: emptyList()
        val mutableInput = input.toMutableList()
        val collectedToolMessages = mutableListOf<Map<String, Any>>()

        while (true) {
            val requestMap = mutableMapOf<String, Any>(
                "model" to model,
                "input" to mutableInput,
                "reasoning" to mapOf("effort" to reasoningEffort, "summary" to "concise")
            )
            if (tools.isNotEmpty()) requestMap["tools"] = tools

            val raw = openai.responses(requestMap)
            val functionCalls = parser.extractFunctionCalls(raw)

            if (functionCalls.isEmpty()) {
                val answer = parser.parse(raw)
                return answer.copy(toolMessages = collectedToolMessages)
            }

            for (call in functionCalls) {
                val callMsg = mapOf(
                    "type" to "function_call",
                    "call_id" to call.callId,
                    "name" to call.name,
                    "arguments" to call.argumentsJson
                )
                val output = toolProvider!!.invoke(call.name, call.argumentsJson)
                val outputMsg = mapOf(
                    "type" to "function_call_output",
                    "call_id" to call.callId,
                    "output" to output
                )
                mutableInput.add(callMsg)
                mutableInput.add(outputMsg)
                collectedToolMessages.add(callMsg)
                collectedToolMessages.add(outputMsg)
            }
        }
    }
}
