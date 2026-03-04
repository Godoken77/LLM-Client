package agent.impl.openai.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import agent.impl.openai.api.dto.Response

interface OpenaiApi {
    suspend fun responses(request: Map<String, Any>): Response
    suspend fun inputTokens(model: String, input: Any): Int
}

class OpenAIClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val gson: Gson = Gson()
) : OpenaiApi {

    override suspend fun responses(request: Map<String, Any>): Response {
        val response = http.post(BASE_URL) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return Response(
            status = response.status.value,
            statusText = response.status.description,
            body = response.body()
        )
    }

    override suspend fun inputTokens(model: String, input: Any): Int {
        val response = http.post(INPUT_TOKENS_URL) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(mapOf("model" to model, "input" to input))
            timeout { requestTimeoutMillis = 15_000 } // пример per-request
        }

        if (!response.status.isSuccess()) return 0
        val bodyStr: String = response.body()
        if (bodyStr.isBlank()) return 0

        val root = gson.fromJson(bodyStr, JsonObject::class.java)
        return root.get("input_tokens")?.asInt ?: 0
    }

    private companion object {
        const val BASE_URL = "https://api.openai.com/v1/responses"
        const val INPUT_TOKENS_URL = "https://api.openai.com/v1/responses/input_tokens"
    }
}