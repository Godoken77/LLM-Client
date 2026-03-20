package agent.impl.openai.memory.layers.engine

import agent.impl.openai.memory.engine.MemoryEngine
import agent.impl.openai.memory.layers.prompt.MemoryPromptBuilder
import agent.impl.openai.memory.layers.repository.MemoryRepository
import agent.impl.openai.memory.layers.router.MemoryRouter

class MemoryLayersEngine(
    private val memoryRepository: MemoryRepository,
    private val memoryRouter: MemoryRouter,
    private val promptBuilder: MemoryPromptBuilder,
    private val systemInstruction: String,
    private val keepLastN: Int = 12,
    private val sessionId: String
) : MemoryEngine {

    override suspend fun onModeActivated() {}

    override suspend fun buildInput(userText: String): List<Map<String, Any>> {
        var memory = memoryRepository.load(sessionId)
        memory = memoryRouter.updateAfterUserMessage(memory, userText)
        memoryRepository.save(sessionId, memory)
        return promptBuilder.buildInput(
            memory = memory,
            systemInstruction = systemInstruction,
            keepLastN = keepLastN,
            userId = sessionId
        )
    }

    override suspend fun saveToolMessages(messages: List<Map<String, Any>>) {
        if (messages.isEmpty()) return
        var memory = memoryRepository.load(sessionId)
        val short = memory.shortTerm.messages.toMutableList()
        short.addAll(messages)
        memory = memory.copy(shortTerm = memory.shortTerm.copy(messages = short))
        memoryRepository.save(sessionId, memory)
    }

    override suspend fun saveAssistantReply(reply: String) {
        var memory = memoryRepository.load(sessionId)
        memory = memoryRouter.updateAfterAssistantMessage(memory, reply)
        memoryRepository.save(sessionId, memory)
    }

    override suspend fun reset() {
        memoryRepository.delete(sessionId)
    }
}
