package ragindexer.pipeline

import kotlinx.coroutines.runBlocking
import ragindexer.chunker.ChunkingStrategy
import ragindexer.chunker.FixedSizeChunker
import ragindexer.chunker.StructuralChunker
import ragindexer.embedding.EmbeddingProvider
import ragindexer.model.DocumentIndex
import ragindexer.model.IndexedChunk
import ragindexer.reader.DocumentReader
import ragindexer.store.IndexStore
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Orchestrates the full RAG indexing pipeline:
 *   1. Read document text from disk.
 *   2. Split text into [Chunk]s using the selected [ChunkingStrategy].
 *   3. Generate embeddings for each chunk via [EmbeddingProvider] (Ollama).
 *   4. Persist the resulting [DocumentIndex] via [IndexStore].
 *
 * @param embeddingProvider  Ollama (or any) embedding backend.
 * @param indexStore         Where to persist the finished index.
 * @param fixedChunkSize     Window size (chars) for [ChunkingStrategy.FIXED_SIZE].
 * @param fixedChunkOverlap  Overlap (chars) for [ChunkingStrategy.FIXED_SIZE].
 * @param minStructuralSize  Minimum chars per section for [ChunkingStrategy.STRUCTURAL].
 */
class IndexingPipeline(
    private val embeddingProvider: EmbeddingProvider,
    private val indexStore: IndexStore,
    fixedChunkSize: Int = 1000,
    fixedChunkOverlap: Int = 200,
    minStructuralSize: Int = 50
) {
    private val fixedSizeChunker = FixedSizeChunker(fixedChunkSize, fixedChunkOverlap)
    private val structuralChunker = StructuralChunker(minStructuralSize)

    /**
     * Indexes [file] using [strategy] and returns the persisted [DocumentIndex].
     *
     * This is a blocking call (bridges coroutines ↔ synchronous callers).
     * Call from a coroutine scope directly via [indexAsync] if preferred.
     */
    fun index(file: File, strategy: ChunkingStrategy): DocumentIndex = runBlocking {
        indexAsync(file, strategy)
    }

    /**
     * Suspending variant of [index] – use from coroutine contexts.
     */
    suspend fun indexAsync(file: File, strategy: ChunkingStrategy): DocumentIndex {
        println("\n[Pipeline] ─── Starting indexing ───────────────────────────────")
        println("[Pipeline] File     : ${file.absolutePath}")
        println("[Pipeline] Strategy : $strategy")

        // Step 1 – Read
        println("[Pipeline] Reading document...")
        val text = DocumentReader.read(file)
        println("[Pipeline] Extracted ${text.length} characters")

        // Step 2 – Chunk
        val chunker = when (strategy) {
            ChunkingStrategy.FIXED_SIZE -> fixedSizeChunker
            ChunkingStrategy.STRUCTURAL -> structuralChunker
        }
        val chunks = chunker.chunk(text, file)
        println("[Pipeline] Generated ${chunks.size} chunks")

        if (chunks.isEmpty()) {
            System.err.println("[Pipeline] WARNING: no chunks produced – document may be empty or too short")
        }

        // Step 3 – Embed
        println("[Pipeline] Embedding via Ollama (model: ${embeddingProvider.modelName})...")
        val indexedChunks = mutableListOf<IndexedChunk>()
        for ((idx, chunk) in chunks.withIndex()) {
            print("\r[Pipeline]   chunk ${idx + 1}/${chunks.size}  ")
            val embedding = embeddingProvider.embed(chunk.text)
            indexedChunks += IndexedChunk(
                text = chunk.text,
                embedding = embedding,
                metadata = chunk.metadata
            )
        }
        println("\r[Pipeline] Embeddings done (${indexedChunks.size} vectors)      ")

        // Step 4 – Persist
        val documentIndex = DocumentIndex(
            indexId = UUID.randomUUID().toString(),
            source = file.absolutePath,
            title = file.nameWithoutExtension,
            strategy = strategy.name,
            model = embeddingProvider.modelName,
            chunks = indexedChunks,
            createdAt = Instant.now().toString(),
            totalChunks = indexedChunks.size
        )
        indexStore.save(documentIndex)

        println("[Pipeline] ─── Indexing complete ──────────────────────────────")
        println("[Pipeline] Index ID : ${documentIndex.indexId}")
        println("[Pipeline] Chunks   : ${documentIndex.totalChunks}")
        return documentIndex
    }

    /**
     * Indexes [file] with BOTH strategies and returns the pair (fixed, structural).
     */
    fun indexBothStrategies(file: File): Pair<DocumentIndex, DocumentIndex> {
        val fixed = index(file, ChunkingStrategy.FIXED_SIZE)
        val structural = index(file, ChunkingStrategy.STRUCTURAL)
        return fixed to structural
    }
}
