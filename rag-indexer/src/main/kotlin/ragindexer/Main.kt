package ragindexer

import ragindexer.chunker.ChunkingStrategy
import ragindexer.embedding.OllamaEmbeddingProvider
import ragindexer.pipeline.IndexingPipeline
import ragindexer.store.JsonIndexStore
import java.io.File

// ─── Entry point ────────────────────────────────────────────────────────────

fun main(args: Array<String>) {
    if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
        printUsage()
        return
    }

    val config = try {
        parseArgs(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("Error: ${e.message}")
        printUsage()
        return
    }

    val file = File(config.filePath)
    if (!file.exists()) {
        System.err.println("Error: file not found – ${config.filePath}")
        return
    }

    OllamaEmbeddingProvider(baseUrl = config.ollamaUrl, modelName = config.model).use { embeddingProvider ->
        val indexStore = JsonIndexStore(File(config.outputDir))
        val pipeline = IndexingPipeline(
            embeddingProvider = embeddingProvider,
            indexStore = indexStore,
            fixedChunkSize = config.chunkSize,
            fixedChunkOverlap = config.chunkOverlap,
            minStructuralSize = config.minStructuralSize
        )

        try {
            if (config.bothStrategies) {
                println("Indexing with BOTH strategies...")
                val (fixed, structural) = pipeline.indexBothStrategies(file)
                println("\nDone.")
                println("  FIXED_SIZE  index ID : ${fixed.indexId}  (${fixed.totalChunks} chunks)")
                println("  STRUCTURAL  index ID : ${structural.indexId}  (${structural.totalChunks} chunks)")
            } else {
                val index = pipeline.index(file, config.strategy)
                println("\nDone.  Index ID: ${index.indexId}  (${index.totalChunks} chunks)")
            }
        } catch (e: Exception) {
            System.err.println("Indexing failed: ${e.message}")
            e.printStackTrace()
        }
    }
}

// ─── Config ─────────────────────────────────────────────────────────────────

data class IndexerConfig(
    val filePath: String,
    val strategy: ChunkingStrategy = ChunkingStrategy.FIXED_SIZE,
    val bothStrategies: Boolean = false,
    val ollamaUrl: String = "http://localhost:11434",
    val model: String = "nomic-embed-text",
    val outputDir: String = "data/indices",
    val chunkSize: Int = 1000,
    val chunkOverlap: Int = 200,
    val minStructuralSize: Int = 50
)

private fun parseArgs(args: Array<String>): IndexerConfig {
    var filePath = ""
    var strategy = ChunkingStrategy.FIXED_SIZE
    var bothStrategies = false
    var ollamaUrl = "http://localhost:11434"
    var model = "nomic-embed-text"
    var outputDir = "data/indices"
    var chunkSize = 1000
    var chunkOverlap = 200
    var minStructuralSize = 50

    fun nextArg(flag: String): String {
        require(i + 1 < args.size) { "Flag '$flag' requires a value but none was provided" }
        return args[++i]
    }

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--file"              -> filePath = nextArg("--file")
            "--strategy"         -> strategy = ChunkingStrategy.fromString(nextArg("--strategy"))
            "--both"             -> bothStrategies = true
            "--ollama-url"       -> ollamaUrl = nextArg("--ollama-url")
            "--model"            -> model = nextArg("--model")
            "--output"           -> outputDir = nextArg("--output")
            "--chunk-size"       -> chunkSize = nextArg("--chunk-size").toInt()
            "--chunk-overlap"    -> chunkOverlap = nextArg("--chunk-overlap").toInt()
            "--min-section-size" -> minStructuralSize = nextArg("--min-section-size").toInt()
            else                 -> if (filePath.isEmpty() && !args[i].startsWith("--")) {
                filePath = args[i]   // positional first argument
            }
        }
        i++
    }

    require(filePath.isNotEmpty()) { "Missing required argument: --file <path>" }

    return IndexerConfig(
        filePath = filePath,
        strategy = strategy,
        bothStrategies = bothStrategies,
        ollamaUrl = ollamaUrl,
        model = model,
        outputDir = outputDir,
        chunkSize = chunkSize,
        chunkOverlap = chunkOverlap,
        minStructuralSize = minStructuralSize
    )
}

private fun printUsage() {
    println(
        """
        RAG Indexer – builds a local vector index from a document using Ollama embeddings.

        Usage:
          rag-indexer --file <path> [options]

        Required:
          --file <path>              Path to the document (txt, md, pdf, doc, docx)

        Chunking:
          --strategy <name>          FIXED_SIZE | STRUCTURAL  (default: FIXED_SIZE)
          --both                     Index with BOTH strategies in one run
          --chunk-size <n>           Characters per chunk for FIXED_SIZE  (default: 1000)
          --chunk-overlap <n>        Overlap characters for FIXED_SIZE    (default: 200)
          --min-section-size <n>     Min chars per section for STRUCTURAL (default: 50)

        Ollama:
          --ollama-url <url>         Ollama base URL  (default: http://localhost:11434)
          --model <name>             Embedding model  (default: nomic-embed-text)

        Output:
          --output <dir>             Index output directory  (default: data/indices)

        Examples:
          rag-indexer --file report.pdf --strategy STRUCTURAL
          rag-indexer --file notes.txt --both --output /tmp/indexes
          rag-indexer --file thesis.docx --strategy FIXED_SIZE --chunk-size 500 --chunk-overlap 100
        """.trimIndent()
    )
}
