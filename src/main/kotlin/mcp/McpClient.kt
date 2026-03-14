package mcp

interface McpClient {
    suspend fun connect()
    suspend fun listTools(): List<McpTool>
    suspend fun callTool(name: String, arguments: Map<String, Any>): McpToolResult
    suspend fun disconnect()
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>? = null
)

data class McpToolResult(
    val content: String,
    val isError: Boolean = false
)
