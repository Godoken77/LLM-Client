package agent.impl.openai.tools

import com.google.gson.Gson
import mcp.McpClient

interface McpToolProvider {
    suspend fun getToolDefinitions(): List<Map<String, Any>>
    suspend fun invoke(name: String, argumentsJson: String): String
}

class McpToolProviderImpl(
    private val mcpClient: McpClient,
    private val gson: Gson = Gson()
) : McpToolProvider {

    override suspend fun getToolDefinitions(): List<Map<String, Any>> =
        mcpClient.listTools().map { tool ->
            buildMap {
                put("type", "function")
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.inputSchema ?: mapOf("type" to "object"))
            }
        }

    @Suppress("UNCHECKED_CAST")
    override suspend fun invoke(name: String, argumentsJson: String): String {
        val args: Map<String, Any> = runCatching {
            gson.fromJson(argumentsJson, Map::class.java) as Map<String, Any>
        }.getOrDefault(emptyMap())
        return mcpClient.callTool(name, args).content
    }
}
