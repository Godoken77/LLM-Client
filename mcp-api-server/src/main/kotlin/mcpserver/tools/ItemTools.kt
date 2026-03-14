package mcpserver.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mcpserver.model.Item
import mcpserver.store.ItemStore

fun Server.registerItemTools() {

    addTool(
        name = "list_items",
        description = "List all items",
    ) { _ ->
        val items = ItemStore.list()
        val text = if (items.isEmpty()) "No items found."
        else items.joinToString("\n") { "id=${it.id} name=${it.name} price=${it.price} description=${it.description}" }
        CallToolResult(content = listOf(TextContent(text)))
    }

    addTool(
        name = "get_item",
        description = "Get an item by ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "The item ID")
                }
            },
            required = listOf("id"),
        ),
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'id' is required")))
        val item = ItemStore.get(id)
        val text = if (item == null) "Item not found: $id"
        else "id=${item.id} name=${item.name} price=${item.price} description=${item.description}"
        CallToolResult(content = listOf(TextContent(text)))
    }

    addTool(
        name = "create_item",
        description = "Create a new item",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Item name")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "Item description")
                }
                putJsonObject("price") {
                    put("type", "number")
                    put("description", "Item price")
                }
            },
            required = listOf("name", "description", "price"),
        ),
    ) { request ->
        val name = request.arguments?.get("name")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'name' is required")))
        val description = request.arguments?.get("description")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'description' is required")))
        val price = request.arguments?.get("price")?.jsonPrimitive?.doubleOrNull
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'price' must be a number")))
        val item = ItemStore.create(Item(name = name, description = description, price = price))
        CallToolResult(content = listOf(TextContent("Created item id=${item.id} name=${item.name} price=${item.price}")))
    }

    addTool(
        name = "update_item",
        description = "Update an existing item by ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "The item ID to update")
                }
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "New name (optional)")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "New description (optional)")
                }
                putJsonObject("price") {
                    put("type", "number")
                    put("description", "New price (optional)")
                }
            },
            required = listOf("id"),
        ),
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'id' is required")))
        val name = request.arguments?.get("name")?.jsonPrimitive?.content
        val description = request.arguments?.get("description")?.jsonPrimitive?.content
        val price = request.arguments?.get("price")?.jsonPrimitive?.doubleOrNull
        val updated = ItemStore.update(id, name, description, price)
        val text = if (updated == null) "Item not found: $id"
        else "Updated item id=${updated.id} name=${updated.name} price=${updated.price}"
        CallToolResult(content = listOf(TextContent(text)))
    }

    addTool(
        name = "delete_item",
        description = "Delete an item by ID",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "The item ID to delete")
                }
            },
            required = listOf("id"),
        ),
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent("Error: 'id' is required")))
        val deleted = ItemStore.delete(id)
        val text = if (deleted) "Deleted item $id" else "Item not found: $id"
        CallToolResult(content = listOf(TextContent(text)))
    }
}
