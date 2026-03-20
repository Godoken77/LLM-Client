package ragindexer.chunker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class FixedSizeChunkerTest {

    private val dummyFile = File("test_document.txt")

    @Test
    fun `empty text produces no chunks`() {
        val chunks = FixedSizeChunker().chunk("", dummyFile)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `blank text produces no chunks`() {
        val chunks = FixedSizeChunker().chunk("   \n  \t  ", dummyFile)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `text shorter than chunkSize produces single chunk`() {
        val text = "Hello world"
        val chunks = FixedSizeChunker(chunkSize = 100, overlap = 10).chunk(text, dummyFile)
        assertEquals(1, chunks.size)
        assertEquals("Hello world", chunks[0].text)
    }

    @Test
    fun `produces correct number of chunks with overlap`() {
        // 1000 chars, chunkSize=300, overlap=100 → step = 200
        // positions: 0, 200, 400, 600, 800  → 5 chunks
        val text = "A".repeat(1000)
        val chunks = FixedSizeChunker(chunkSize = 300, overlap = 100).chunk(text, dummyFile)
        assertEquals(5, chunks.size)
    }

    @Test
    fun `metadata contains correct strategy and indices`() {
        val text = "A".repeat(500)
        val chunks = FixedSizeChunker(chunkSize = 200, overlap = 50).chunk(text, dummyFile)

        chunks.forEachIndexed { i, chunk ->
            assertEquals(ChunkingStrategy.FIXED_SIZE.name, chunk.metadata.strategy)
            assertEquals(i, chunk.metadata.chunkIndex)
            assertEquals(dummyFile.nameWithoutExtension, chunk.metadata.title)
            assertEquals(null, chunk.metadata.section)
        }
    }

    @Test
    fun `chunk IDs are unique`() {
        val text = "B".repeat(2000)
        val chunks = FixedSizeChunker(chunkSize = 300, overlap = 50).chunk(text, dummyFile)
        val ids = chunks.map { it.metadata.chunkId }.toSet()
        assertEquals(chunks.size, ids.size)
    }

    @Test
    fun `overlapping chunks share text at boundaries`() {
        val text = "0123456789"
        // chunkSize=6, overlap=2 → step=4
        // chunk 0: 0-5 = "012345"
        // chunk 1: 4-9 = "456789"
        val chunks = FixedSizeChunker(chunkSize = 6, overlap = 2).chunk(text, dummyFile)
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].text.endsWith("45"))
        assertTrue(chunks[1].text.startsWith("45"))
    }
}
