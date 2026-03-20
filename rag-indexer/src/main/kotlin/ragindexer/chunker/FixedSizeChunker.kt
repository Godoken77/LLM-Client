package ragindexer.chunker

import ragindexer.model.Chunk
import ragindexer.model.ChunkMetadata
import java.io.File
import java.util.UUID

/**
 * Splits a document into overlapping fixed-size character windows.
 *
 * @param chunkSize  Maximum number of characters per chunk (default 1000).
 * @param overlap    Number of characters shared between consecutive chunks (default 200).
 *                   Overlap preserves context across chunk boundaries, which improves
 *                   retrieval quality for sentences that straddle a split point.
 */
class FixedSizeChunker(
    private val chunkSize: Int = 1000,
    private val overlap: Int = 200
) : Chunker {

    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(overlap >= 0) { "overlap must be non-negative" }
        require(overlap < chunkSize) { "overlap must be less than chunkSize" }
    }

    override val strategy: ChunkingStrategy = ChunkingStrategy.FIXED_SIZE

    override fun chunk(text: String, file: File): List<Chunk> {
        val normalised = text.replace("\r\n", "\n")
        val title = file.nameWithoutExtension
        val source = file.absolutePath
        val chunks = mutableListOf<Chunk>()

        var start = 0
        var chunkIndex = 0

        while (start < normalised.length) {
            val end = minOf(start + chunkSize, normalised.length)
            val chunkText = normalised.substring(start, end).trim()

            if (chunkText.isNotBlank()) {
                chunks += Chunk(
                    text = chunkText,
                    metadata = ChunkMetadata(
                        chunkId = UUID.randomUUID().toString(),
                        source = source,
                        title = title,
                        section = null,
                        chunkIndex = chunkIndex++,
                        strategy = strategy.name
                    )
                )
            }

            // Advance by (chunkSize - overlap) to create the sliding window.
            start += chunkSize - overlap
        }

        return chunks
    }
}
