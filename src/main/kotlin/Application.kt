import dependency.Dependency
import kotlinx.coroutines.runBlocking
import mcp.StdioMcpClient
import network.startService
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

fun main() {
    setUpOutput()
    printMcpTools()
    startService(Dependency)
}

private fun setUpOutput() {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))
}

private fun printMcpTools() = runBlocking {
    val client = StdioMcpClient(
        serverCommand = listOf(
            "java", "-jar",
            "mcp-api-server/build/libs/mcp-api-server-1.0-SNAPSHOT-all.jar"
        )
    )
    try {
        client.connect()
        val tools = client.listTools()
        println("=== MCP Tools (${tools.size}) ===")
        tools.forEach { tool ->
            println("  [${tool.name}] ${tool.description}")
        }
    } catch (e: Exception) {
        System.err.println("Failed to list MCP tools: ${e.message}")
    } finally {
        client.disconnect()
    }
}