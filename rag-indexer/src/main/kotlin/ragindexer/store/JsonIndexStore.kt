package ragindexer.store

import com.google.gson.GsonBuilder
import ragindexer.model.DocumentIndex
import java.io.File

/**
 * File-based JSON implementation of [IndexStore].
 *
 * Directory layout:
 * ```
 * <outputDir>/
 *   fixed_size/
 *     <indexId>.json
 *   structural/
 *     <indexId>.json
 * ```
 *
 * Each file is a pretty-printed [DocumentIndex] JSON.
 *
 * @param outputDir Root directory for all index files (created if absent).
 */
class JsonIndexStore(private val outputDir: File = File("data/indices")) : IndexStore {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    init {
        outputDir.mkdirs()
    }

    override fun save(index: DocumentIndex) {
        val strategyDir = File(outputDir, index.strategy.lowercase())
        strategyDir.mkdirs()
        val file = File(strategyDir, "${index.indexId}.json")
        file.writeText(gson.toJson(index), Charsets.UTF_8)
        println("[IndexStore] Saved: ${file.absolutePath}  (${index.totalChunks} chunks)")
    }

    override fun load(indexId: String): DocumentIndex? {
        return outputDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" && it.nameWithoutExtension == indexId }
            .firstOrNull()
            ?.let { parseFile(it) }
    }

    override fun listAll(): List<String> =
        outputDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .map { it.nameWithoutExtension }
            .toList()

    override fun listByStrategy(strategy: String): List<DocumentIndex> {
        val strategyDir = File(outputDir, strategy.lowercase())
        if (!strategyDir.exists()) return emptyList()
        return strategyDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { parseFile(it) }
            ?: emptyList()
    }

    private fun parseFile(file: File): DocumentIndex? =
        runCatching { gson.fromJson(file.readText(Charsets.UTF_8), DocumentIndex::class.java) }
            .onFailure { e -> System.err.println("[IndexStore] Failed to parse ${file.name}: ${e.message}") }
            .getOrNull()
}
