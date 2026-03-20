package ragindexer.embedding

/**
 * Abstraction over an embedding model.
 *
 * Implementations must be coroutine-safe and thread-safe.
 * Each call may perform I/O (e.g. HTTP to Ollama), so callers should
 * invoke from a coroutine context with an appropriate dispatcher.
 */
interface EmbeddingProvider {
    /** Name of the model used to produce embeddings (informational, persisted in the index). */
    val modelName: String

    /**
     * Returns the embedding vector for [text].
     * The dimensionality depends on the backing model.
     */
    suspend fun embed(text: String): List<Double>

    /**
     * Convenience batch method – default implementation calls [embed] sequentially.
     * Override for models that support native batching.
     */
    suspend fun embedBatch(texts: List<String>): List<List<Double>> =
        texts.map { embed(it) }
}
