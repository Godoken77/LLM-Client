package mcpserver.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType { ONCE, PERIODIC }

@Serializable
data class TaskResult(
    val timestamp: Long,
    val data: String,
)

@Serializable
data class ScheduledTask(
    val id: String,
    val name: String,
    val description: String,
    val type: TaskType,
    val intervalSeconds: Long? = null,
    val nextRunAt: Long,
    val lastRunAt: Long? = null,
    val enabled: Boolean = true,
    val results: List<TaskResult> = emptyList(),
)
