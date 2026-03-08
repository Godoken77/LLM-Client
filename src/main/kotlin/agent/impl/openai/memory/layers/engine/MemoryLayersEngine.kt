package agent.impl.openai.memory.layers.engine

import agent.impl.openai.memory.engine.BuiltInput
import agent.impl.openai.memory.engine.MemoryEngine
import agent.impl.openai.memory.layers.prompt.MemoryPromptBuilder
import agent.impl.openai.memory.layers.repository.MemoryRepository
import agent.impl.openai.memory.layers.router.MemoryRouter
import agent.impl.openai.model.ModelInstruction
import store.SessionId

class MemoryLayersEngine(
    private val memoryRepository: MemoryRepository,
    private val memoryRouter: MemoryRouter,
    private val promptBuilder: MemoryPromptBuilder,
    private val systemInstruction: ModelInstruction,
    private val keepLastN: Int = 12,
    private val sessionId: SessionId
) : MemoryEngine {

    override suspend fun onModeActivated(sessionId: String) {
        // ничего не нужно
    }

    override suspend fun buildInput(sessionId: String, userText: String): BuiltInput {
        var memory = memoryRepository.load(sessionId)

        memory = memoryRouter.updateAfterUserMessage(memory, userText)
        memoryRepository.save(sessionId, memory)

        val input = promptBuilder.buildInput(
            memory = memory,
            systemInstruction = systemInstruction.instruction,
            keepLastN = keepLastN,
            userId = sessionId
        )

        return BuiltInput(input = input)
    }

    override suspend fun saveAssistantReply(sessionId: String, reply: String) {
        var memory = memoryRepository.load(sessionId)
        memory = memoryRouter.updateAfterAssistantMessage(memory, reply)
        memoryRepository.save(sessionId, memory)
    }

    override suspend fun reset(sessionId: String) {
        memoryRepository.delete(sessionId)
    }
}