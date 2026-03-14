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

    private fun read(): MutableMap<String, ScheduledTask> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<List<ScheduledTask>>(file.readText())
                .associateBy { it.id }
                .toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun write(tasks: Map<String, ScheduledTask>) {
        file.writeText(json.encodeToString(tasks.values.toList()))
    }

    suspend fun list(): List<ScheduledTask> = mutex.withLock { read().values.toList() }

    suspend fun get(id: String): ScheduledTask? = mutex.withLock { read()[id] }

    suspend fun create(
        name: String,
        description: String,
        type: TaskType,
        intervalSeconds: Long?,
        nextRunAt: Long,
    ): ScheduledTask = mutex.withLock {
        val tasks = read()
        val task = ScheduledTask(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            type = type,
            intervalSeconds = intervalSeconds,
            nextRunAt = nextRunAt,
        )
        tasks[task.id] = task
        write(tasks)
        task
    }

    suspend fun appendResult(id: String, data: String): Boolean = mutex.withLock {
        val tasks = read()
        val task = tasks[id] ?: return@withLock false
        val result = TaskResult(timestamp = System.currentTimeMillis(), data = data)
        tasks[id] = task.copy(
            results = (task.results + result).takeLast(100),
            lastRunAt = result.timestamp,
        )
        write(tasks)
        true
    }

    suspend fun updateNextRun(id: String, nextRunAt: Long) = mutex.withLock {
        val tasks = read()
        val task = tasks[id] ?: return@withLock
        tasks[id] = task.copy(nextRunAt = nextRunAt)
        write(tasks)
    }

    suspend fun disable(id: String): Boolean = mutex.withLock {
        val tasks = read()
        val task = tasks[id] ?: return@withLock false
        tasks[id] = task.copy(enabled = false)
        write(tasks)
        true
    }

    suspend fun delete(id: String): Boolean = mutex.withLock {
        val tasks = read()
        val removed = tasks.remove(id) != null
        if (removed) write(tasks)
        removed
    }

    suspend fun getDueTasks(nowMillis: Long): List<ScheduledTask> = mutex.withLock {
        read().values.filter { it.enabled && it.nextRunAt <= nowMillis }
    }

    suspend fun clear() = mutex.withLock { write(emptyMap()) }
}
