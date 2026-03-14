package mcpserver.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mcpserver.model.TaskType
import mcpserver.store.TaskStore
import java.time.Instant

fun Server.registerSchedulerTools(store: TaskStore) {

    // ── schedule_reminder ────────────────────────────────────────────────────
    addTool(
        name = "schedule_reminder",
        description = "Schedule a one-time reminder that fires after a given delay in seconds",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Reminder name")
                }
                putJsonObject("message") {
                    put("type", "string")
                    put("description", "Reminder message / content")
                }
                putJsonObject("delay_seconds") {
                    put("type", "number")
                    put("description", "Delay in seconds from now before the reminder fires")
                }
            },
            required = listOf("name", "message", "delay_seconds"),
        ),
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'name' is required")), isError = true)
        val message = request.arguments?.get("message")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'message' is required")), isError = true)
        val delaySeconds = request.arguments?.get("delay_seconds")?.jsonPrimitive?.content
            ?.toDoubleOrNull()?.toLong()
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'delay_seconds' must be a number")), isError = true)

        val fireAt = System.currentTimeMillis() + delaySeconds * 1000L
        val task = store.create(
            name = name,
            description = message,
            type = TaskType.ONCE,
            intervalSeconds = null,
            nextRunAt = fireAt,
        )
        CallToolResult(
            content = listOf(
                TextContent("Reminder '${task.name}' scheduled. id=${task.id}, fires at ${Instant.ofEpochMilli(fireAt)}")
            )
        )
    }

    // ── schedule_periodic ────────────────────────────────────────────────────
    addTool(
        name = "schedule_periodic",
        description = "Schedule a periodic task that runs at a fixed interval and records a timestamp on each tick",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Task name")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "What this task monitors or collects")
                }
                putJsonObject("interval_seconds") {
                    put("type", "number")
                    put("description", "Interval in seconds between executions (minimum 1)")
                }
            },
            required = listOf("name", "description", "interval_seconds"),
        ),
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'name' is required")), isError = true)
        val description = request.arguments?.get("description")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'description' is required")), isError = true)
        val intervalSeconds = request.arguments?.get("interval_seconds")?.jsonPrimitive?.content
            ?.toDoubleOrNull()?.toLong()
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'interval_seconds' must be a number")), isError = true)
        if (intervalSeconds < 1L)
            return@addTool CallToolResult(content = listOf(TextContent("Error: interval_seconds must be >= 1")), isError = true)

        val task = store.create(
            name = name,
            description = description,
            type = TaskType.PERIODIC,
            intervalSeconds = intervalSeconds,
            nextRunAt = System.currentTimeMillis() + intervalSeconds * 1000L,
        )
        CallToolResult(
            content = listOf(
                TextContent(
                    "Periodic task '${task.name}' created. id=${task.id}, interval=${task.intervalSeconds}s, first run in ${task.intervalSeconds}s"
                )
            )
        )
    }

    // ── record_data_point ────────────────────────────────────────────────────
    addTool(
        name = "record_data_point",
        description = "Manually record a data point for an existing scheduled task",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("task_id") {
                    put("type", "string")
                    put("description", "The task ID to record data for")
                }
                putJsonObject("data") {
                    put("type", "string")
                    put("description", "The data string to store")
                }
            },
            required = listOf("task_id", "data"),
        ),
    ) { request ->
        val taskId = request.arguments?.get("task_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'task_id' is required")), isError = true)
        val data = request.arguments?.get("data")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'data' is required")), isError = true)
        val ok = store.appendResult(taskId, data)
        if (!ok) return@addTool CallToolResult(content = listOf(TextContent("Task not found: $taskId")), isError = true)
        CallToolResult(content = listOf(TextContent("Data point recorded for task $taskId")))
    }

    // ── list_scheduled_tasks ─────────────────────────────────────────────────
    addTool(
        name = "list_scheduled_tasks",
        description = "List all scheduled tasks with their current status and next run time",
    ) { _ ->
        val tasks = store.list()
        if (tasks.isEmpty()) return@addTool CallToolResult(content = listOf(TextContent("No scheduled tasks.")))
        val now = System.currentTimeMillis()
        val lines = tasks.map { t ->
            val status = when {
                !t.enabled -> "DISABLED"
                t.nextRunAt <= now -> "DUE"
                else -> "ACTIVE"
            }
            val nextIn = if (t.enabled) {
                val diffSec = (t.nextRunAt - now) / 1000
                if (diffSec > 0) "next in ${diffSec}s" else "due now"
            } else "—"
            "[${t.id.take(8)}] ${t.name} | ${t.type} | $status | $nextIn | results=${t.results.size} | ${t.description}"
        }
        CallToolResult(content = listOf(TextContent(lines.joinToString("\n"))))
    }

    // ── get_task_results ─────────────────────────────────────────────────────
    addTool(
        name = "get_task_results",
        description = "Get recent results and stats for a scheduled task",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("task_id") {
                    put("type", "string")
                    put("description", "The task ID")
                }
                putJsonObject("limit") {
                    put("type", "number")
                    put("description", "Max number of results to return (default 10)")
                }
            },
            required = listOf("task_id"),
        ),
    ) { request ->
        val taskId = request.arguments?.get("task_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'task_id' is required")), isError = true)
        val limit = request.arguments?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: 10
        val task = store.get(taskId)
            ?: return@addTool CallToolResult(content = listOf(TextContent("Task not found: $taskId")), isError = true)

        val recent = task.results.takeLast(limit)
        val sb = StringBuilder()
        sb.appendLine("=== ${task.name} (${task.type}) ===")
        sb.appendLine("Description : ${task.description}")
        sb.appendLine("Enabled     : ${task.enabled}")
        sb.appendLine("Total stored: ${task.results.size}  (showing last ${recent.size})")
        if (task.intervalSeconds != null) sb.appendLine("Interval    : ${task.intervalSeconds}s")
        if (task.lastRunAt != null) sb.appendLine("Last run    : ${Instant.ofEpochMilli(task.lastRunAt)}")
        sb.appendLine("--- Results ---")
        recent.forEach { r -> sb.appendLine("  [${Instant.ofEpochMilli(r.timestamp)}] ${r.data}") }
        CallToolResult(content = listOf(TextContent(sb.toString().trimEnd())))
    }

    // ── get_summary ──────────────────────────────────────────────────────────
    addTool(
        name = "get_summary",
        description = "Get an aggregated summary of all scheduled tasks, data points, and recent activity",
    ) { _ ->
        val tasks = store.list()
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        sb.appendLine("=== Scheduler Summary ===")
        sb.appendLine("Generated   : ${Instant.ofEpochMilli(now)}")
        sb.appendLine("Total tasks : ${tasks.size}  (active=${tasks.count { it.enabled }}, disabled=${tasks.count { !it.enabled }})")
        sb.appendLine("Total points: ${tasks.sumOf { it.results.size }}")
        if (tasks.isEmpty()) {
            sb.appendLine("(no tasks yet)")
        } else {
            sb.appendLine()
            tasks.forEach { t ->
                val statusLabel = if (t.enabled) "ACTIVE" else "DISABLED"
                sb.appendLine("[${t.id.take(8)}] ${t.name}  [$statusLabel / ${t.type}]")
                sb.appendLine("  ${t.description}")
                if (t.intervalSeconds != null) {
                    val mins = t.intervalSeconds / 60
                    sb.appendLine("  Interval : ${t.intervalSeconds}s${if (mins > 0) " (${mins}min)" else ""}")
                }
                if (t.enabled) {
                    val diffSec = (t.nextRunAt - now) / 1000
                    sb.appendLine("  Next run : ${if (diffSec > 0) "in ${diffSec}s" else "due now"}")
                }
                if (t.lastRunAt != null) sb.appendLine("  Last run : ${Instant.ofEpochMilli(t.lastRunAt)}")
                sb.appendLine("  Points   : ${t.results.size}")
                t.results.lastOrNull()?.let { last ->
                    sb.appendLine("  Latest   : ${last.data}")
                }
            }
        }
        CallToolResult(content = listOf(TextContent(sb.toString().trimEnd())))
    }

    // ── cancel_task ──────────────────────────────────────────────────────────
    addTool(
        name = "cancel_task",
        description = "Cancel (disable) a scheduled task so it no longer fires automatically",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("task_id") {
                    put("type", "string")
                    put("description", "The task ID to cancel")
                }
            },
            required = listOf("task_id"),
        ),
    ) { request ->
        val taskId = request.arguments?.get("task_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'task_id' is required")), isError = true)
        val ok = store.disable(taskId)
        if (!ok) return@addTool CallToolResult(content = listOf(TextContent("Task not found: $taskId")), isError = true)
        CallToolResult(content = listOf(TextContent("Task $taskId cancelled")))
    }
}
