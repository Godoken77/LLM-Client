package ragindexer.chunker

import ragindexer.model.Chunk
import java.io.File

interface Chunker {
    val strategy: ChunkingStrategy
    fun chunk(text: String, file: File): List<Chunk>
}
