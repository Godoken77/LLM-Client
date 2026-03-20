package ragindexer.model

/**
 * Metadata attached to every chunk — persisted alongside text and embedding.
 */
data class ChunkMetadata(
    val chunkId: String,
    /** Absolute path to the source document. */
    val source: String,
    /** Document title (filename without extension). */
    val title: String,
    /** Section heading from the structural chunker; null for fixed-size strategy. */
    val section: String?,
    /** Sequential position of this chunk within the document (0-based). */
    val chunkIndex: Int,
    /** Which chunking strategy produced this chunk. */
    val strategy: String
)

/** Raw chunk before embedding. */
data class Chunk(
    val text: String,
    val metadata: ChunkMetadata
)

/** Chunk enriched with its vector embedding. */
data class IndexedChunk(
    val text: String,
    val embedding: List<Double>,
    val metadata: ChunkMetadata
)

/**
 * Top-level index file for one document + strategy combination.
 * Saved to disk as a single JSON file.
 *
 * Designed to be extended with similarity-search in future iterations:
 * pass [chunks] to a vector store or run cosine-similarity in-process.
 */
data class DocumentIndex(
    val indexId: String,
    /** Absolute path to the original document. */
    val source: String,
    val title: String,
    val strategy: String,
    /** Ollama model used to generate embeddings. */
    val model: String,
    val chunks: List<IndexedChunk>,
    val createdAt: String,
    val totalChunks: Int
)
