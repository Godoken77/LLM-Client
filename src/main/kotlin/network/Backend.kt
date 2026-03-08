package network

import agent.impl.openai.agentImpl.AgentMemoryMode
import agent.impl.openai.api.dto.AgentMemoryModeResponse
import agent.impl.openai.api.dto.SetAgentMemoryModeRequest
import agent.impl.openai.api.dto.SetAgentMemoryModeResponse
import agent.impl.openai.memory.context.ContextMode
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import dependency.Dependency
import io.ktor.server.http.content.default
import io.ktor.server.http.content.staticResources
import model.ChatRequest
import model.ChatResponse
import model.ContextInfoResponse
import model.HistoryItem
import model.HistoryResponse
import model.ResetResponse
import model.SetContextRequest
import model.SetContextResponse
import ui.htmlPage
import java.util.UUID
import kotlin.collections.get
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.text.isNullOrBlank

fun startService(dependency: Dependency) {

    val conversationStore = dependency.conversationStore
    val agentStore = dependency.agentStore

    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) { gson() }

        install(CORS) {
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowCredentials = true
            anyHost()
        }

        environment.monitor.subscribe(ApplicationStopped) {
            dependency.close()
        }

        routing {
            staticResources("/", "web") {
                default("index.html")
            }

            post("/api/chat") {
                ensureSessionId(call)
                val req = call.receive<ChatRequest>()

                val agent = agentStore.getOrCreate()
                val result = agent.ask(req.message)

                call.respond(ChatResponse(reply = result.reply, stats = result.stats))
            }

            get("/api/pid") {
                call.respondText(ProcessHandle.current().pid().toString(), ContentType.Text.Plain)
            }

            post("/api/reset") {
                ensureSessionId(call)
                val agent = agentStore.getOrCreate()
                agent.reset()
                agentStore.remove()
                call.respond(ResetResponse(ok = true))
            }

            get("/api/history") {
                val sid = ensureSessionId(call)

                val state = conversationStore.loadState(sid)

                val items = mutableListOf<HistoryItem>()

                if (state.summary.isNotBlank()) {
                    items += HistoryItem(role = "summary", text = state.summary)
                }

                state.messages.forEach { msg ->
                    val role = msg["role"] as? String ?: return@forEach
                    val content = msg["content"] as? List<*> ?: return@forEach
                    val first = content.firstOrNull() as? Map<*, *> ?: return@forEach
                    val text = first["text"] as? String ?: return@forEach

                    // developer/system обычно не показываем в UI
                    if (role == "developer" || role == "system") return@forEach

                    items += HistoryItem(role = role, text = text)
                }

                call.respond(HistoryResponse(items))
            }

            get("/api/context") {
                ensureSessionId(call)
                val agent = agentStore.getOrCreate()

                val current = agent.getContextMode()

                call.respond(
                    ContextInfoResponse(
                        mode = current.name,
                        available = ContextMode.entries.map { it.name }
                    )
                )
            }

            post("/api/context") {
                ensureSessionId(call)
                val agent = agentStore.getOrCreate()

                val req = call.receive<SetContextRequest>()
                val newMode = runCatching { ContextMode.valueOf(req.mode) }.getOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        SetContextResponse(ok = false, mode = req.mode, message = "Unknown mode: ${req.mode}")
                    )

                agent.setContextMode(newMode)

                call.respond(SetContextResponse(ok = true, mode = newMode.name))
            }

            get("/api/memory-mode") {
                ensureSessionId(call)
                val agent = agentStore.getOrCreate()

                call.respond(
                    AgentMemoryModeResponse(
                        mode = agent.getAgentMemoryMode().name,
                        available = AgentMemoryMode.entries.map { it.name }
                    )
                )
            }

            post("/api/memory-mode") {
                ensureSessionId(call)
                val agent = agentStore.getOrCreate()

                val req = call.receive<SetAgentMemoryModeRequest>()
                val newMode = runCatching { AgentMemoryMode.valueOf(req.mode) }.getOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        SetAgentMemoryModeResponse(
                            ok = false,
                            mode = req.mode,
                            message = "Unknown mode: ${req.mode}"
                        )
                    )

                agent.setAgentMemoryMode(newMode)

                call.respond(SetAgentMemoryModeResponse(ok = true, mode = newMode.name))
            }
        }
    }.start(wait = true)
}

@OptIn(ExperimentalAtomicApi::class)
private fun ensureSessionId(call: ApplicationCall): String {
    val existing = call.request.cookies["SID"]
    if (!existing.isNullOrBlank()) {
        Dependency.sessionId.store(existing)
        return existing
    }

    val sid = UUID.randomUUID().toString()
    call.response.cookies.append(
        Cookie(
            name = "SID",
            value = sid,
            httpOnly = true,
            path = "/",
            secure = false, // для локалки
        )
    )
    Dependency.sessionId.store(sid)
    return sid
}