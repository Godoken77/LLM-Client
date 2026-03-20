package ragindexer.chunker

import ragindexer.model.Chunk
import ragindexer.model.ChunkMetadata
import java.io.File
import java.util.UUID

/**
 * Splits a document at structural boundaries detected in the extracted text.
 *
 * Detection priority (highest to lowest):
 *  1. Markdown ATX headings:  `# Heading`, `## Sub`, `### Sub-sub`
 *  2. Setext-style headings:  a non-blank line followed by `===` or `---` underline
 *  3. Numbered sections:      lines like `1. Introduction`, `2.3 Methods`
 *  4. Page-break character:   `\f` inserted by PDF/Word extractors
 *  5. Fallback – paragraph:   double blank lines separate paragraphs
 *
 * Chunks shorter than [minChunkSize] characters are merged into the next chunk
 * rather than being discarded, so no content is lost.
 *
 * @param minChunkSize Minimum characters for a standalone chunk (default 50).
 *                     Shorter candidate sections are merged with the following chunk.
 */
class StructuralChunker(
    private val minChunkSize: Int = 50
) : Chunker {

    override val strategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL

    // ATX markdown heading: optional leading whitespace, 1-6 #, space, heading text
    private val atxHeading = Regex("""^\s{0,3}(#{1,6})\s+(.+?)\s*$""")

    // Setext underline: three or more = or - characters filling (most of) a line
    private val setextUnderline = Regex("""^[=\-]{3,}\s*$""")

    // Numbered section: "1.", "2.3", "II.", "A." at line start (optionally indented)
    private val numberedSection = Regex("""^\s{0,3}(?:\d+(?:\.\d+)*\.?|[IVXivx]+\.|[A-Z]\.)\s+\S.{2,}$""")

    // Page-break character produced by PDFBox / Apache POI
    private val pageBreak = Regex("""\f""")

    override fun chunk(text: String, file: File): List<Chunk> {
        val normalised = text.replace("\r\n", "\n")
        val title = file.nameWithoutExtension
        val source = file.absolutePath

        val rawSections = extractSections(normalised, title)
        val merged = mergeShort(rawSections)

        return merged.mapIndexed { idx, (heading, body) ->
            Chunk(
                text = body.trim(),
                metadata = ChunkMetadata(
                    chunkId = UUID.randomUUID().toString(),
                    source = source,
                    title = title,
                    section = heading,
                    chunkIndex = idx,
                    strategy = strategy.name
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Produces a list of (sectionHeading, bodyText) pairs from raw text lines. */
    private fun extractSections(text: String, documentTitle: String): List<Pair<String, String>> {
        // Split pages first (page-break characters), then handle each page
        val pages = text.split(pageBreak)
        val allSections = mutableListOf<Pair<String, String>>()

        for ((pageIndex, page) in pages.withIndex()) {
            val pageTitle = if (pages.size > 1) "Page ${pageIndex + 1}" else documentTitle
            allSections += extractHeadingSections(page.lines(), pageTitle)
        }

        return allSections.ifEmpty { listOf(documentTitle to text) }
    }

    /**
     * Scans lines for heading markers and splits into sections.
     * Falls back to paragraph splitting if no headings are found.
     */
    private fun extractHeadingSections(
        lines: List<String>,
        fallbackTitle: String
    ): List<Pair<String, String>> {
        val sections = mutableListOf<Pair<String, String>>()
        var currentHeading = fallbackTitle
        val currentBody = StringBuilder()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // 1. ATX heading
            val atxMatch = atxHeading.find(line)
            if (atxMatch != null) {
                if (currentBody.isNotBlank()) sections += currentHeading to currentBody.toString()
                currentBody.clear()
                currentHeading = atxMatch.groupValues[2]
                i++
                continue
            }

            // 2. Setext heading (current line = title, next line = underline)
            if (i + 1 < lines.size
                && setextUnderline.matches(lines[i + 1])
                && line.isNotBlank()
            ) {
                if (currentBody.isNotBlank()) sections += currentHeading to currentBody.toString()
                currentBody.clear()
                currentHeading = line.trim()
                i += 2 // skip both the heading and the underline
                continue
            }

            // 3. Numbered section (only promote to heading if body already has content)
            if (currentBody.isNotBlank() && numberedSection.matches(line)) {
                sections += currentHeading to currentBody.toString()
                currentBody.clear()
                currentHeading = line.trim()
                i++
                continue
            }

            currentBody.appendLine(line)
            i++
        }

        if (currentBody.isNotBlank()) sections += currentHeading to currentBody.toString()

        // If we found no structural markers, fall back to paragraph splitting
        return if (sections.isEmpty() || (sections.size == 1 && sections[0].first == fallbackTitle && !hasHeading(sections[0].second))) {
            splitByParagraphs(lines, fallbackTitle)
        } else {
            sections
        }
    }

    /** Returns true if the text appears to contain at least one heading. */
    private fun hasHeading(text: String): Boolean =
        text.lines().any { atxHeading.containsMatchIn(it) || numberedSection.matches(it) }

    /** Paragraph-based fallback: splits on double blank lines. */
    private fun splitByParagraphs(lines: List<String>, title: String): List<Pair<String, String>> {
        val sections = mutableListOf<Pair<String, String>>()
        val current = StringBuilder()
        var paraIndex = 1
        var prevWasBlank = false

        for (line in lines) {
            val isBlank = line.isBlank()
            if (isBlank && prevWasBlank && current.isNotBlank()) {
                sections += "Paragraph $paraIndex" to current.toString().trim()
                current.clear()
                paraIndex++
            } else if (!isBlank) {
                current.appendLine(line)
            }
            prevWasBlank = isBlank
        }

        if (current.isNotBlank()) sections += "Paragraph $paraIndex" to current.toString().trim()

        return sections.ifEmpty { listOf(title to lines.joinToString("\n")) }
    }

    /**
     * Merges sections whose body is shorter than [minChunkSize] into the next section,
     * so that every returned chunk contains meaningful content.
     */
    private fun mergeShort(sections: List<Pair<String, String>>): List<Pair<String, String>> {
        if (sections.isEmpty()) return sections

        val result = mutableListOf<Pair<String, String>>()
        var pending: Pair<String, String>? = null

        for (section in sections) {
            val (heading, body) = section
            val trimmed = body.trim()

            if (trimmed.length < minChunkSize) {
                // Accumulate into the pending buffer
                pending = if (pending == null) {
                    heading to trimmed
                } else {
                    pending!!.first to "${pending!!.second}\n\n$trimmed"
                }
            } else {
                if (pending != null) {
                    // Prepend accumulated content to the current body
                    result += heading to "${pending!!.second}\n\n$trimmed"
                    pending = null
                } else {
                    result += heading to trimmed
                }
            }
        }

        // Flush remaining pending content
        if (pending != null) result += pending!!

        return result
    }
}
