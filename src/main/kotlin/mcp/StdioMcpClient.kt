package mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

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
            input = process.inputStream,
            output = process.outputStream
        )
        client.connect(transport)
    }

    override suspend fun listTools(): List<McpTool> {
        return client.listTools().tools.map { tool ->
            McpTool(
                name = tool.name,
                description = tool.description ?: ""
            )
        }
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
