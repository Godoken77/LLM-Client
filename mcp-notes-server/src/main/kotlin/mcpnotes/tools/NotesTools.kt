package mcpnotes.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mcpnotes.store.NoteStore
import java.time.Instant

fun Server.registerNotesTools(store: NoteStore) {

    // ── create_note ──────────────────────────────────────────────────────────
    addTool(
        name = "create_note",
        description = "Create a new note with a title and content",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Note title")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "Note content (body text)")
                }
            },
            required = listOf("title", "content"),
        ),
    ) { request ->
        val title = request.arguments?.get("title")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'title' is required")), isError = true)
        val content = request.arguments?.get("content")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'content' is required")), isError = true)

        val note = store.create(title, content)
        CallToolResult(
            content = listOf(TextContent("Note created. id=${note.id}, title='${note.title}'"))
        )
    }

    // ── list_notes ───────────────────────────────────────────────────────────
    addTool(
        name = "list_notes",
        description = "List all notes with their IDs, titles and creation times",
    ) { _ ->
        val notes = store.list()
        if (notes.isEmpty()) return@addTool CallToolResult(content = listOf(TextContent("No notes found.")))
        val lines = notes.map { n ->
            "[${n.id.take(8)}] '${n.title}' — created ${Instant.ofEpochMilli(n.createdAt)}"
        }
        CallToolResult(content = listOf(TextContent(lines.joinToString("\n"))))
    }

    // ── get_note ─────────────────────────────────────────────────────────────
    addTool(
        name = "get_note",
        description = "Get the full content of a note by its ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("note_id") {
                    put("type", "string")
                    put("description", "The note ID")
                }
            },
            required = listOf("note_id"),
        ),
    ) { request ->
        val id = request.arguments?.get("note_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'note_id' is required")), isError = true)
        val note = store.get(id)
            ?: return@addTool CallToolResult(content = listOf(TextContent("Note not found: $id")), isError = true)

        val sb = StringBuilder()
        sb.appendLine("=== ${note.title} ===")
        sb.appendLine("id      : ${note.id}")
        sb.appendLine("created : ${Instant.ofEpochMilli(note.createdAt)}")
        sb.appendLine("updated : ${Instant.ofEpochMilli(note.updatedAt)}")
        sb.appendLine("---")
        sb.append(note.content)
        CallToolResult(content = listOf(TextContent(sb.toString())))
    }

    // ── update_note ──────────────────────────────────────────────────────────
    addTool(
        name = "update_note",
        description = "Update title or content of an existing note",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("note_id") {
                    put("type", "string")
                    put("description", "The note ID to update")
                }
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "New title (optional)")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "New content (optional)")
                }
            },
            required = listOf("note_id"),
        ),
    ) { request ->
        val id = request.arguments?.get("note_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'note_id' is required")), isError = true)
        val title = request.arguments?.get("title")?.jsonPrimitive?.content
        val content = request.arguments?.get("content")?.jsonPrimitive?.content

        val updated = store.update(id, title, content)
            ?: return@addTool CallToolResult(content = listOf(TextContent("Note not found: $id")), isError = true)
        CallToolResult(content = listOf(TextContent("Note updated. id=${updated.id}, title='${updated.title}'")))
    }

    // ── delete_note ──────────────────────────────────────────────────────────
    addTool(
        name = "delete_note",
        description = "Delete a note by its ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("note_id") {
                    put("type", "string")
                    put("description", "The note ID to delete")
                }
            },
            required = listOf("note_id"),
        ),
    ) { request ->
        val id = request.arguments?.get("note_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'note_id' is required")), isError = true)
        val ok = store.delete(id)
        if (!ok) return@addTool CallToolResult(content = listOf(TextContent("Note not found: $id")), isError = true)
        CallToolResult(content = listOf(TextContent("Note $id deleted")))
    }
}
