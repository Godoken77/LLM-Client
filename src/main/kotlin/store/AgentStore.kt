package store

import agent.Agent

typealias SessionId = String

interface AgentStore {
    fun getOrCreate(): Agent
    fun remove()
}