package org.example.session

import java.util.concurrent.ConcurrentHashMap

// ------------------- Per-session locks -------------------
object SessionLocks {
    private val locks = ConcurrentHashMap<String, Any>()
    fun lockFor(sid: String): Any = locks.computeIfAbsent(sid) { Any() }
}