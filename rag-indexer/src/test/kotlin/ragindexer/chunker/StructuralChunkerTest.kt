package ragindexer.chunker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class StructuralChunkerTest {

    private val dummyFile = File("test_document.txt")

    // ── ATX headings ─────────────────────────────────────────────────────────

    @Test
    fun `splits markdown by ATX headings`() {
        val text = """
            # Introduction
            This is the introduction section with some content here.

            ## Background
            Background content goes here with enough words to pass min size.

            ## Conclusion
            Final thoughts and summary of the document content.
        """.trimIndent()

        val chunks = StructuralChunker(minChunkSize = 10).chunk(text, dummyFile)

        val headings = chunks.map { it.metadata.section }
        assertTrue(headings.any { it?.contains("Introduction") == true }, "Expected 'Introduction' heading")
        assertTrue(headings.any { it?.contains("Background") == true }, "Expected 'Background' heading")
        assertTrue(headings.any { it?.contains("Conclusion") == true }, "Expected 'Conclusion' heading")
    }

    @Test
    fun `heading text is captured in section metadata`() {
        val text = """
            # My Heading
            Some sufficiently long content for this section to be kept as its own chunk.
        """.trimIndent()

        val chunks = StructuralChunker(minChunkSize = 10).chunk(text, dummyFile)
        assertTrue(chunks.any { it.metadata.section == "My Heading" })
    }

    // ── Setext headings ───────────────────────────────────────────────────────

    @Test
    fun `splits by setext-style headings`() {
        val text = """
            Overview
            ========
            This overview section contains a decent amount of content to work with.

            Details
            -------
            These are the detailed notes and information for this particular section.
        """.trimIndent()

        val chunks = StructuralChunker(minChunkSize = 10).chunk(text, dummyFile)
        val headings = chunks.map { it.metadata.section }
        assertTrue(headings.any { it == "Overview" }, "Expected 'Overview' heading")
        assertTrue(headings.any { it == "Details" }, "Expected 'Details' heading")
    }

    // ── Paragraph fallback ───────────────────────────────────────────────────

    @Test
    fun `falls back to paragraph splitting for flat text`() {
        val text = buildString {
            append("First paragraph has multiple lines of readable content.\n")
            append("It continues on and on with enough words.\n")
            append("\n\n")
            append("Second paragraph is also long enough to be kept.\n")
            append("More text here to ensure minimum size is met.\n")
        }

        val chunks = StructuralChunker(minChunkSize = 30).chunk(text, dummyFile)
        assertTrue(chunks.size >= 2, "Expected at least 2 paragraph chunks, got ${chunks.size}")
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    @Test
    fun `metadata has correct strategy and sequential chunkIndex`() {
        val text = """
            # A
            Content for section A which is long enough to survive.

            # B
            Content for section B which is also long enough here.
        """.trimIndent()

        val chunks = StructuralChunker(minChunkSize = 10).chunk(text, dummyFile)
        chunks.forEachIndexed { i, chunk ->
            assertEquals(ChunkingStrategy.STRUCTURAL.name, chunk.metadata.strategy)
            assertEquals(i, chunk.metadata.chunkIndex)
            assertEquals(dummyFile.nameWithoutExtension, chunk.metadata.title)
        }
    }

    @Test
    fun `chunk IDs are unique`() {
        val text = """
            # Sec 1
            Content one with sufficient characters.

            # Sec 2
            Content two with sufficient characters.

            # Sec 3
            Content three with sufficient characters here.
        """.trimIndent()

        val chunks = StructuralChunker(minChunkSize = 10).chunk(text, dummyFile)
        val ids = chunks.map { it.metadata.chunkId }.toSet()
        assertEquals(chunks.size, ids.size)
    }

    // ── Short section merging ─────────────────────────────────────────────────

    @Test
    fun `short sections are merged rather than discarded`() {
        val text = """
            # Short
            Hi.

            # Long section title
            This section contains enough content to be meaningful on its own.
        """.trimIndent()

        val chunks = StructuralChunker(minChunkSize = 100).chunk(text, dummyFile)
        // "Hi." is too short; it should be merged into the next chunk's body
        val combinedText = chunks.joinToString(" ") { it.text }
        assertTrue(combinedText.contains("Hi."), "Short section content should be merged, not dropped")
    }

    // ── Empty input ───────────────────────────────────────────────────────────

    @Test
    fun `empty text returns no chunks`() {
        val chunks = StructuralChunker().chunk("", dummyFile)
        assertTrue(chunks.isEmpty())
    }
}
