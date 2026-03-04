package agent.impl.openai.compsessor

import agent.impl.openai.summarizer.Summarizer
import store.ConversationState
import kotlin.math.min

interface ConversationCompressor {
    suspend fun maybeCompress(state: ConversationState): ConversationState
}

class ConversationCompressorImpl(
    private val keepLastN: Int = DEFAULT_KEEP_N,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    private val summarizer: Summarizer
) : ConversationCompressor {

    override suspend fun maybeCompress(state: ConversationState): ConversationState {

        if (keepLastN <= 0) {
            return state
        }

        val msgs = state.messages.toMutableList()

        val head = mutableListOf<Map<String, Any>>()
        val body = mutableListOf<Map<String, Any>>()

        for ((idx, m) in msgs.withIndex()) {
            val role = m["role"] as? String ?: ""
            if (idx == 0 && (role == "developer" || role == "system")) head.add(m) else body.add(m)
        }

        if (body.size <= keepLastN) {
            return state
        }

        val old = body.subList(0, body.size - keepLastN).toList()
        val tail = body.subList(body.size - keepLastN, body.size).toMutableList()

        var newSummary = state.summary
        var cursor = 0

        while (cursor < old.size) {
            val from = cursor
            val to = min(cursor + chunkSize, old.size)
            val chunk = old.subList(from, to)

            newSummary = summarizer.summarize(newSummary, chunk)
            cursor += chunkSize
        }

        return ConversationState(
            summary = newSummary,
            messages = (head + tail).toMutableList()
        )
    }

    private companion object {
        const val DEFAULT_KEEP_N = 12
        const val DEFAULT_CHUNK_SIZE = 10
    }
}