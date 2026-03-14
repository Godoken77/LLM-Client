package mcpserver.store

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mcpserver.model.ScheduledTask
import mcpserver.model.TaskResult
import mcpserver.model.TaskType
import java.io.File
import java.util.UUID

class TaskStore(dataDir: File) {

    private val file = File(dataDir, "scheduled_tasks.json").also { dataDir.mkdirs() }
    private val mutex = Mutex()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val cache: MutableMap<String, ScheduledTask> = loadFromDisk()

    private fun loadFromDisk(): MutableMap<String, ScheduledTask> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<List<ScheduledTask>>(file.readText())
                .associateBy { it.id }
                .toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun flush() {
        file.writeText(json.encodeToString(cache.values.toList()))
    }

    suspend fun list(): List<ScheduledTask> = mutex.withLock { cache.values.toList() }

    suspend fun get(id: String): ScheduledTask? = mutex.withLock { cache[id] }

    suspend fun create(
        name: String,
        description: String,
        type: TaskType,
        intervalSeconds: Long?,
        nextRunAt: Long,
    ): ScheduledTask = mutex.withLock {
        val task = ScheduledTask(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            type = type,
            intervalSeconds = intervalSeconds,
            nextRunAt = nextRunAt,
        )
        cache[task.id] = task
        flush()
        task
    }

    suspend fun appendResult(id: String, data: String): Boolean = mutex.withLock {
        val task = cache[id] ?: return@withLock false
        val result = TaskResult(timestamp = System.currentTimeMillis(), data = data)
        cache[id] = task.copy(
            results = (task.results + result).takeLast(100),
            lastRunAt = result.timestamp,
        )
        flush()
        true
    }

    suspend fun updateNextRun(id: String, nextRunAt: Long) = mutex.withLock {
        val task = cache[id] ?: return@withLock
        cache[id] = task.copy(nextRunAt = nextRunAt)
        flush()
    }

    suspend fun disable(id: String): Boolean = mutex.withLock {
        val task = cache[id] ?: return@withLock false
        cache[id] = task.copy(enabled = false)
        flush()
        true
    }

    suspend fun delete(id: String): Boolean = mutex.withLock {
        val removed = cache.remove(id) != null
        if (removed) flush()
        removed
    }

    suspend fun getDueTasks(nowMillis: Long): List<ScheduledTask> = mutex.withLock {
        cache.values.filter { it.enabled && it.nextRunAt <= nowMillis }
    }

    suspend fun clear() = mutex.withLock {
        cache.clear()
        flush()
    }
}
