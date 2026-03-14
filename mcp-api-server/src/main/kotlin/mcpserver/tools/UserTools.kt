package mcpserver.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mcpserver.model.User
import mcpserver.store.UserStore

fun Server.registerUserTools() {

    addTool(
        name = "list_users",
        description = "List all users",
    ) { _ ->
        val users = UserStore.list()
        val text = if (users.isEmpty()) "No users found."
        else users.joinToString("\n") { "id=${it.id} name=${it.name} email=${it.email}" }
        CallToolResult(content = listOf(TextContent(text)))
    }

    addTool(
        name = "get_user",
        description = "Get a user by ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "The user ID")
                }
            },
            required = listOf("id"),
        ),
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'id' is required")), isError = true)
        val user = UserStore.get(id)
            ?: return@addTool CallToolResult(content = listOf(TextContent("User not found: $id")), isError = true)
        CallToolResult(content = listOf(TextContent("id=${user.id} name=${user.name} email=${user.email}")))
    }

    addTool(
        name = "create_user",
        description = "Create a new user",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "User display name")
                }
                putJsonObject("email") {
                    put("type", "string")
                    put("description", "User email address")
                }
            },
            required = listOf("name", "email"),
        ),
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'name' is required")), isError = true)
        val email = request.arguments?.get("email")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'email' is required")), isError = true)
        val user = UserStore.create(User(name = name, email = email))
        CallToolResult(content = listOf(TextContent("Created user id=${user.id} name=${user.name} email=${user.email}")))
    }

    addTool(
        name = "update_user",
        description = "Update an existing user by ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "The user ID to update")
                }
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "New name (optional)")
                }
                putJsonObject("email") {
                    put("type", "string")
                    put("description", "New email (optional)")
                }
            },
            required = listOf("id"),
        ),
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'id' is required")), isError = true)
        val name = request.arguments?.get("name")?.jsonPrimitive?.content
        val email = request.arguments?.get("email")?.jsonPrimitive?.content
        val updated = UserStore.update(id, name, email)
            ?: return@addTool CallToolResult(content = listOf(TextContent("User not found: $id")), isError = true)
        CallToolResult(content = listOf(TextContent("Updated user id=${updated.id} name=${updated.name} email=${updated.email}")))
    }

    addTool(
        name = "delete_user",
        description = "Delete a user by ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "The user ID to delete")
                }
            },
            required = listOf("id"),
        ),
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'id' is required")), isError = true)
        val deleted = UserStore.delete(id)
        if (!deleted) return@addTool CallToolResult(content = listOf(TextContent("User not found: $id")), isError = true)
        CallToolResult(content = listOf(TextContent("Deleted user $id")))
    }
}
