package network

import agent.impl.openai.context.ContextMode
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
            get("/") {
                call.respondText(htmlPage(), ContentType.Text.Html)
            }

            post("/api/chat") {
                val sid = ensureSessionId(call)
                val req = call.receive<ChatRequest>()

                val agent = agentStore.getOrCreate(sid)
                val result = agent.ask(req.message)

                call.respond(ChatResponse(reply = result.reply, stats = result.stats))
            }

            get("/api/pid") {
                call.respondText(ProcessHandle.current().pid().toString(), ContentType.Text.Plain)
            }

            post("/api/reset") {
                val sid = ensureSessionId(call)
                val agent = agentStore.getOrCreate(sid)
                agent.reset()
                agentStore.remove(sid)
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
                val sid = ensureSessionId(call)
                val agent = agentStore.getOrCreate(sid)

                val current = agent.getContextMode()

                call.respond(
                    ContextInfoResponse(
                        mode = current.name,
                        available = ContextMode.entries.map { it.name }
                    )
                )
            }

            post("/api/context") {
                val sid = ensureSessionId(call)
                val agent = agentStore.getOrCreate(sid)

                val req = call.receive<SetContextRequest>()
                val newMode = runCatching { ContextMode.valueOf(req.mode) }.getOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        SetContextResponse(ok = false, mode = req.mode, message = "Unknown mode: ${req.mode}")
                    )

                agent.setContextMode(newMode)

                call.respond(SetContextResponse(ok = true, mode = newMode.name))
            }
        }
    }.start(wait = true)
}

private fun ensureSessionId(call: ApplicationCall): String {
    val existing = call.request.cookies["SID"]
    if (!existing.isNullOrBlank()) return existing

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
    return sid
}