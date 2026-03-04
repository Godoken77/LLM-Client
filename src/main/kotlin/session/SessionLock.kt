package session

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

// ------------------- Per-session locks -------------------
object SessionLocks {
    private val map = ConcurrentHashMap<String, Mutex>()
    fun mutexFor(sessionId: String): Mutex = map.computeIfAbsent(sessionId) { Mutex() }
}