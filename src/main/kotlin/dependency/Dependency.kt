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
import org.example.store.impl.AgentStore
import org.example.store.ConversationStore
import org.example.store.JsonConversationStore
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

    val openaiApi: OpenaiApi by lazy {
        OpenAIClient(
            http = httpClient,
            apiKey = System.getenv("OPENAI_API_KEY") ?:  error("Open API key not found")
        )
    }

    val agentStore = AgentStore(conversationStore, openaiApi)

    fun close() = httpClient.close()
}