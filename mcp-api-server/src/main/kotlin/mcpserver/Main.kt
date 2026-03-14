package mcpserver

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import mcpserver.scheduler.TaskScheduler
import mcpserver.store.TaskStore
import mcpserver.tools.registerItemTools
import mcpserver.tools.registerSchedulerTools
import mcpserver.tools.registerUserTools
import java.io.File

fun main(): Unit = runBlocking {
    System.err.println("[mcp-api-server] Starting...")

    val taskStore = TaskStore(File("data/scheduler"))
    val scheduler = TaskScheduler(taskStore)

    val server = Server(
        serverInfo = Implementation(name = "mcp-api-server", version = "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    )

    server.registerUserTools()
    server.registerItemTools()
    server.registerSchedulerTools(taskStore)

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )

    val done = Job()
    val session = server.createSession(transport)
    session.onClose { done.complete() }

    scheduler.start(this)
    System.err.println("[mcp-api-server] Connected, waiting for requests...")
    done.join()
}
