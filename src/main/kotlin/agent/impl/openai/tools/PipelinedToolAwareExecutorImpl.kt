package agent.impl.openai.tools

import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.model.Answer
import agent.impl.openai.responseparser.ResponseParser

/**
 * Strict pipeline executor: runs each McpToolProvider as a separate stage in order.
 * Each stage sees the full accumulated context from all previous stages.
 * A stage runs its own tool-calling loop (via ToolAwareOpenaiExecutorImpl) until
 * the LLM produces a final text reply, then that reply becomes context for the next stage.
 */
class PipelinedToolAwareExecutorImpl(
    private val openai: OpenaiApi,
    private val parser: ResponseParser,
    private val stages: List<McpToolProvider>,
) : ToolAwareOpenaiExecutor {

    override suspend fun execute(
        model: String,
        reasoningEffort: String,
        input: List<Map<String, Any>>,
    ): Answer {
        val accumulatedInput = input.toMutableList()
        val allToolMessages = mutableListOf<Map<String, Any>>()
        var lastAnswer = Answer(reply = "", stats = null)

        for (stage in stages) {
            val stageExecutor = ToolAwareOpenaiExecutorImpl(openai, parser, stage)
            val stageAnswer = stageExecutor.execute(model, reasoningEffort, accumulatedInput.toList())

            allToolMessages.addAll(stageAnswer.toolMessages)
            accumulatedInput.addAll(stageAnswer.toolMessages)

            if (stageAnswer.reply.isNotBlank()) {
                accumulatedInput.add(
                    mapOf(
                        "role" to "assistant",
                        "content" to listOf(mapOf("type" to "output_text", "text" to stageAnswer.reply))
                    )
                )
            }

            lastAnswer = stageAnswer
        }

        return lastAnswer.copy(toolMessages = allToolMessages)
    }
}
