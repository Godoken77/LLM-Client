package store

import agent.Agent

typealias SessionId = String

interface AgentStore {
    fun getOrCreate(sessionId: SessionId): Agent
    fun remove(sessionId: SessionId)
}