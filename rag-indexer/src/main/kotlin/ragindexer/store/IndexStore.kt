package ragindexer.store

import ragindexer.model.DocumentIndex

/**
 * Persistence contract for document indexes.
 *
 * Designed to be extended in future iterations with similarity-search capabilities.
 * A future [SearchableIndexStore] can extend this interface and add:
 *
 *   fun search(queryEmbedding: List<Double>, topK: Int = 5): List<ScoredChunk>
 *
 * The [DocumentIndex.chunks] list provides all the data needed for cosine-similarity
 * or ANN (approximate nearest-neighbour) search without schema changes.
 */
interface IndexStore {
    /** Persist [index] to the backing store. Overwrites if the same [DocumentIndex.indexId] exists. */
    fun save(index: DocumentIndex)

    /** Load a previously saved index by its [indexId], or null if not found. */
    fun load(indexId: String): DocumentIndex?

    /** Return all index IDs available in the store. */
    fun listAll(): List<String>

    /** Return all indexes for a given [strategy] name (e.g. "FIXED_SIZE"). */
    fun listByStrategy(strategy: String): List<DocumentIndex>
}
