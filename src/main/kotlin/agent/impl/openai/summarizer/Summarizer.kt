package org.example.agent.impl.openai.summarizer

import org.example.agent.impl.openai.api.OpenaiApi
import org.example.agent.impl.openai.messages.MessageFactory
import org.example.agent.impl.openai.model.ModelVersion
import org.example.agent.impl.openai.responseparser.ResponseParser

interface Summarizer {
    suspend fun summarize(existingSummary: String, chunk: List<Map<String, Any>>): String
}

class LlmSummarizer(
    private val openai: OpenaiApi,
    private val responseParser: ResponseParser,
    private val messages: MessageFactory,
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION
) : Summarizer {

    override suspend fun summarize(existingSummary: String, chunk: List<Map<String, Any>>): String {
        val chunkText = chunk.joinToString("\n") { m ->
            val role = m["role"] as? String ?: "unknown"
            val text = messages.extractText(m)
            "${role.uppercase()}: $text"
        }

        val prompt = """
Ты сжимаешь историю диалога.
Текущее summary (если пустое — значит его ещё нет):
$existingSummary

Новые сообщения, которые нужно добавить в summary:
$chunkText

Сформируй обновлённое summary:
- сохрани факты, предпочтения пользователя, принятые решения
- сохрани важные имена/числа/ограничения
- не пиши лишних деталей
- 8–15 строк максимум
""".trimIndent()

        val req = mapOf(
            "model" to model,
            "input" to listOf(
                messages.msg("developer", "Ты — модуль суммаризации. Пиши только summary, без вступлений."),
                messages.msg("user", prompt)
            ),
            "reasoning" to mapOf("effort" to "low")
        )

        val raw = openai.responses(req)
        return responseParser.parse(raw).reply.trim()
    }
}