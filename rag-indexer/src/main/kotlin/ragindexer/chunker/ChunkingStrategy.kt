package ragindexer.chunker

/**
 * Available chunking strategies.
 *
 * - [FIXED_SIZE]   – splits text into windows of N characters with configurable overlap.
 *                   Fast, deterministic, works on any document type.
 *
 * - [STRUCTURAL]   – splits text at detected section boundaries (markdown headings,
 *                   underline-style headings, numbered sections, paragraph breaks).
 *                   Preserves semantic units; better retrieval quality for structured docs.
 */
enum class ChunkingStrategy {
    FIXED_SIZE,
    STRUCTURAL;

    companion object {
        fun fromString(value: String): ChunkingStrategy =
            when (value.uppercase().replace("-", "_").replace(" ", "_")) {
                "FIXED_SIZE", "FIXED" -> FIXED_SIZE
                "STRUCTURAL", "STRUCT" -> STRUCTURAL
                else -> throw IllegalArgumentException(
                    "Unknown strategy '$value'. Use FIXED_SIZE or STRUCTURAL."
                )
            }
    }
}
