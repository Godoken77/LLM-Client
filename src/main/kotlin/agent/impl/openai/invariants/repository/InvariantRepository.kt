package agent.impl.openai.invariants.repository

import agent.impl.openai.invariants.InvariantSet
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

interface InvariantRepository {
    fun load(scopeId: String): InvariantSet
    fun save(scopeId: String, invariants: InvariantSet)
    fun delete(scopeId: String)
}

class FileInvariantRepository(
    private val dataDir: File = File("./data/invariants"),
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : InvariantRepository {

    init {
        if (!dataDir.exists()) dataDir.mkdirs()
    }

    override fun load(scopeId: String): InvariantSet {
        val file = fileFor(scopeId)
        if (!file.exists()) return InvariantSet()

        return try {
            file.reader(Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, InvariantSet::class.java) ?: InvariantSet()
            }
        } catch (_: Exception) {
            InvariantSet()
        }
    }

    override fun save(scopeId: String, invariants: InvariantSet) {
        val file = fileFor(scopeId)
        if (!file.parentFile.exists()) file.parentFile.mkdirs()

        val tmp = File(file.absolutePath + ".tmp")
        tmp.writer(Charsets.UTF_8).use { writer ->
            gson.toJson(invariants, writer)
        }

        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    override fun delete(scopeId: String) {
        val file = fileFor(scopeId)
        if (file.exists()) file.delete()
    }

    private fun fileFor(scopeId: String): File {
        val safe = scopeId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dataDir, "$safe.json")
    }
}