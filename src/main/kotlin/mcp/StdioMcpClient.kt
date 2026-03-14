package mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class StdioMcpClient(
    private val serverCommand: List<String>
) : McpClient {

    private val client = Client(
        clientInfo = Implementation(name = "llm-client", version = "1.0.0")
    )

    private var serverProcess: Process? = null

    override suspend fun connect() {
        val process = ProcessBuilder(serverCommand).start()
        serverProcess = process
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
        client.connect(transport)
    }

    override suspend fun listTools(): List<McpTool> {
        return client.listTools().tools.map { tool ->
            McpTool(
                name = tool.name,
                description = tool.description ?: "",
                inputSchema = buildInputSchema(tool.inputSchema)
            )
        }
    }

    private fun buildInputSchema(schema: ToolSchema): Map<String, Any> {
        val result = mutableMapOf<String, Any>("type" to "object")
        schema.properties?.let { result["properties"] = it.toAny() }
        schema.required?.let { if (it.isNotEmpty()) result["required"] = it }
        return result
    }

    private fun JsonElement.toAny(): Any = when (this) {
        is JsonObject -> entries.associate { it.key to it.value.toAny() }
        is JsonArray -> map { it.toAny() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull!!
            longOrNull != null -> longOrNull!!
            doubleOrNull != null -> doubleOrNull!!
            else -> content
        }
        JsonNull -> "null"
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any>): McpToolResult {
        val result = client.callTool(name = name, arguments = arguments)
        val text = result.content
            .filterIsInstance<TextContent>()
            .joinToString("\n") { it.text }
        return McpToolResult(
            content = text,
            isError = result.isError ?: false
        )
    }

    override suspend fun disconnect() {
        client.close()
        serverProcess?.destroy()
    }
}
