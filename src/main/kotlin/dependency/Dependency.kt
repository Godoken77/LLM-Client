package org.example.dependency

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.serialization.gson.gson
import org.example.agent.impl.openai.api.OpenAIClient
import org.example.agent.impl.openai.api.OpenaiApi
import org.example.agent.impl.openai.compsessor.ConversationCompressorImpl
import org.example.agent.impl.openai.conversation.ConversationRepositoryImpl
import org.example.agent.impl.openai.messages.MessageFactoryImpl
import org.example.agent.impl.openai.prompt.PromptBuilderImpl
import org.example.agent.impl.openai.responseparser.GsonResponseParserImpl
import org.example.agent.impl.openai.statenormalizer.StateNormalizerImpl
import org.example.agent.impl.openai.summarizer.LlmSummarizer
import org.example.dependency.Dependency.httpClient
import org.example.store.ConversationStore
import org.example.store.JsonConversationStore
import org.example.store.impl.AgentStore
import java.nio.file.Paths

object Dependency {
    val conversationStore: ConversationStore = JsonConversationStore(Paths.get("data"))

    val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { gson() }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 90_000
                requestTimeoutMillis = 120_000
            }
            defaultRequest { accept(ContentType.Application.Json) }
        }
    }

    val agentStore = AgentStore(
       dependency = OpenaiDependency
    )

    fun close() = httpClient.close()
}

object OpenaiDependency {

    val openaiApi: OpenaiApi by lazy {
        OpenAIClient(
            http = httpClient,
            apiKey = System.getenv("OPENAI_API_KEY") ?:  error("Open API key not found")
        )
    }

    val msgFactory = MessageFactoryImpl()
    val normalizer = StateNormalizerImpl(msgFactory)
    val prompts = PromptBuilderImpl(msgFactory)
    val parser = GsonResponseParserImpl()

    val conversationRepository = ConversationRepositoryImpl(Dependency.conversationStore)

    private val summarizer = LlmSummarizer(
        openai = openaiApi,
        responseParser = parser,
        messages = msgFactory
    )

    val compressor = ConversationCompressorImpl(
        keepLastN = 12,
        chunkSize = 10,
        summarizer = summarizer
    )
}