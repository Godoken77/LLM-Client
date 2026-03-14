package mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

class HttpMcpClient(
    private val serverUrl: String
) : McpClient {

    private val httpClient = HttpClient(CIO) { install(SSE) }

    private val client = Client(
        clientInfo = Implementation(name = "llm-client", version = "1.0.0")
    )

    override suspend fun connect() {
        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = serverUrl
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
        httpClient.close()
    }
}
