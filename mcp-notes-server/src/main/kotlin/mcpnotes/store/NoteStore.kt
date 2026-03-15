package mcpnotes.store

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mcpnotes.model.Note
import java.io.File
import java.util.UUID

class NoteStore(dataDir: File) {

    private val file = File(dataDir, "notes.json").also { dataDir.mkdirs() }
    private val mutex = Mutex()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val cache: MutableMap<String, Note> = loadFromDisk()

    private fun loadFromDisk(): MutableMap<String, Note> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<List<Note>>(file.readText())
                .associateBy { it.id }
                .toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun flush() {
        file.writeText(json.encodeToString(cache.values.toList()))
    }

    suspend fun list(): List<Note> = mutex.withLock { cache.values.sortedByDescending { it.updatedAt } }

    suspend fun get(id: String): Note? = mutex.withLock { cache[id] }

    suspend fun create(title: String, content: String): Note = mutex.withLock {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            createdAt = now,
            updatedAt = now,
        )
        cache[note.id] = note
        flush()
        note
    }

    suspend fun update(id: String, title: String?, content: String?): Note? = mutex.withLock {
        val existing = cache[id] ?: return@withLock null
        val updated = existing.copy(
            title = title ?: existing.title,
            content = content ?: existing.content,
            updatedAt = System.currentTimeMillis(),
        )
        cache[id] = updated
        flush()
        updated
    }

    suspend fun delete(id: String): Boolean = mutex.withLock {
        val removed = cache.remove(id) != null
        if (removed) flush()
        removed
    }
}
