package agent.impl.openai.memory.layers.repository

import agent.impl.openai.memory.layers.model.MemoryState
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

interface MemoryRepository {
    fun load(sessionId: String): MemoryState
    fun save(sessionId: String, state: MemoryState)
    fun delete(sessionId: String)
}

class FileMemoryRepository(
    private val dataDir: File = File("./data"),
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : MemoryRepository {

    init {
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
    }

    override fun load(sessionId: String): MemoryState {
        val file = fileFor(sessionId)
        if (!file.exists()) return MemoryState()

        return try {
            file.reader(Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, MemoryState::class.java) ?: MemoryState()
            }
        } catch (_: Exception) {
            MemoryState()
        }
    }

    override fun save(sessionId: String, state: MemoryState) {
        val file = fileFor(sessionId)
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        val tempFile = File(file.absolutePath + ".tmp")
        tempFile.writer(Charsets.UTF_8).use { writer ->
            gson.toJson(state, writer)
        }

        if (file.exists()) {
            file.delete()
        }
        tempFile.renameTo(file)
    }

    override fun delete(sessionId: String) {
        val file = fileFor(sessionId)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun fileFor(sessionId: String): File {
        return File(dataDir, "${sanitizeSessionId(sessionId)}.json")
    }

    private fun sanitizeSessionId(sessionId: String): String {
        return sessionId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}