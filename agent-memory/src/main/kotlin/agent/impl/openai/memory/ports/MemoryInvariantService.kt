package agent.impl.openai.memory.ports

interface MemoryInvariantService {
    fun buildInvariantPrompt(scopeId: String): String
}
