import dependency.Dependency
import kotlinx.coroutines.runBlocking
import network.startService
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

fun main() {
    setUpOutput()
    connectMcpClient()
    startService(Dependency)
}

private fun setUpOutput() {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))
}

private fun connectMcpClient() = runBlocking {
    try {
        Dependency.mcpClient.connect()
        val tools = Dependency.mcpClient.listTools()
        println("=== MCP Tools (${tools.size}) ===")
        tools.forEach { tool ->
            println("  [${tool.name}] ${tool.description}")
        }
    } catch (e: Exception) {
        System.err.println("Failed to connect MCP client: ${e.message}")
    }
}
