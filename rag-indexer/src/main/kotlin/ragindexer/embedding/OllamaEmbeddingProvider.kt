package ragindexer.embedding

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Calls the local Ollama `/api/embeddings` endpoint to generate embeddings.
 *
 * Compatible with Ollama ≥ 0.1 (legacy single-text endpoint).
 *
 * Default model `nomic-embed-text` is a compact, high-quality embedding model
 * that can be pulled with: `ollama pull nomic-embed-text`
 *
 * @param baseUrl   Base URL of the running Ollama instance (default: http://localhost:11434).
 * @param modelName Ollama model name to use for embeddings.
 */
class OllamaEmbeddingProvider(
    private val baseUrl: String = "http://localhost:11434",
    override val modelName: String = "nomic-embed-text"
) : EmbeddingProvider {

    private val jsonMediaType = "application/json".toMediaType()
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private data class EmbedRequest(val model: String, val prompt: String)
    private data class EmbedResponse(val embedding: List<Double>)

    override suspend fun embed(text: String): List<Double> = withContext(Dispatchers.IO) {
        val requestBody = gson.toJson(EmbedRequest(modelName, text))
            .toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$baseUrl/api/embeddings")
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: error("Empty response body from Ollama at $baseUrl")

        if (!response.isSuccessful) {
            error("Ollama returned HTTP ${response.code}: $responseBody")
        }

        gson.fromJson(responseBody, EmbedResponse::class.java).embedding
            ?: error("Ollama response missing 'embedding' field: $responseBody")
    }

    /**
     * Sends texts sequentially to Ollama.
     * If Ollama is upgraded to support batch natively this can be overridden.
     */
    override suspend fun embedBatch(texts: List<String>): List<List<Double>> =
        texts.map { embed(it) }
}
