package mcpnotes

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
import mcpnotes.store.NoteStore
import mcpnotes.tools.registerNotesTools
import java.io.File

fun main(): Unit = runBlocking {
    System.err.println("[mcp-notes-server] Starting...")

    val noteStore = NoteStore(File("data/notes"))

    val server = Server(
        serverInfo = Implementation(name = "mcp-notes-server", version = "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    )

    server.registerNotesTools(noteStore)

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )

    val done = Job()
    val session = server.createSession(transport)
    session.onClose { done.complete() }

    System.err.println("[mcp-notes-server] Connected, waiting for requests...")
    done.join()
}
