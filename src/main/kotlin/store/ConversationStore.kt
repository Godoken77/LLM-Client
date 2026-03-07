package store

import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

typealias Messages = MutableList<Map<String, Any>>

data class ConversationState(
    val summary: String = "",
    val messages: Messages = mutableListOf(),
    val facts: MutableMap<String, String> = mutableMapOf(),
)

interface ConversationStore {
    fun loadState(sessionId: String): ConversationState
    fun saveState(sessionId: String, state: ConversationState)
    fun delete(sessionId: String)
}

class JsonConversationStore(
    private val baseDir: Path = Paths.get("data")
) : ConversationStore {

    private val gson = Gson()
    private val type = object : com.google.gson.reflect.TypeToken<ConversationState>() {}.type

    init { if (!Files.exists(baseDir)) Files.createDirectories(baseDir) }

    override fun loadState(sessionId: String): ConversationState {
        val file = baseDir.resolve("$sessionId.json")
        if (!Files.exists(file)) return ConversationState()
        val json = Files.readString(file, StandardCharsets.UTF_8)
        return gson.fromJson(json, type) ?: ConversationState()
    }

    override fun saveState(sessionId: String, state: ConversationState) {
        val file = baseDir.resolve("$sessionId.json")
        Files.writeString(file, gson.toJson(state), StandardCharsets.UTF_8)
    }

    override fun delete(sessionId: String) {
        val file = baseDir.resolve("$sessionId.json")
        if (Files.exists(file)) Files.delete(file)
    }
}