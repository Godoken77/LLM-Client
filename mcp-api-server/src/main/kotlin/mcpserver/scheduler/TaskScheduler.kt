package mcpserver.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mcpserver.model.TaskType
import mcpserver.store.TaskStore
import java.time.Instant

class TaskScheduler(private val store: TaskStore) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            System.err.println("[Scheduler] Started — checking every 5 seconds")
            while (isActive) {
                runCatching { tick() }.onFailure { e ->
                    System.err.println("[Scheduler] Error during tick: ${e.message}")
                }
                delay(5_000)
            }
        }
    }

    suspend fun tickForTest() = tick()

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        store.getDueTasks(now).forEach { task ->
            val ts = Instant.ofEpochMilli(now).toString()
            System.err.println("[Scheduler] Firing '${task.name}' (${task.type})")
            store.appendResult(task.id, "Executed at $ts")
            when (task.type) {
                TaskType.PERIODIC -> {
                    val interval = task.intervalSeconds ?: run {
                        System.err.println("[Scheduler] '${task.name}' is PERIODIC but has no intervalSeconds — disabling")
                        store.disable(task.id)
                        return@forEach
                    }
                    store.updateNextRun(task.id, now + interval * 1000L)
                }
                TaskType.ONCE -> store.disable(task.id)
            }
        }
    }
}
