import dependency.Dependency
import kotlinx.coroutines.runBlocking
import network.startService
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

fun main() {
    setUpOutput()
    connectMcpClients()
    startService(Dependency)
}

private fun setUpOutput() {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))
}

private fun connectMcpClients() = runBlocking {
    val servers = listOf(
        "scheduler" to Dependency.mcpClient,
        "notes"     to Dependency.notesClient,
        "weather"   to Dependency.weatherClient,
    )
    for ((name, client) in servers) {
        try {
            client.connect()
            val tools = client.listTools()
            println("=== MCP [$name] — ${tools.size} tools ===")
            tools.forEach { tool -> println("  [${tool.name}] ${tool.description}") }
        } catch (e: Exception) {
            System.err.println("Failed to connect MCP [$name]: ${e.message}")
        }
    }
}
