package agent.impl.openai

import kotlinx.coroutines.runBlocking
import agent.Agent
import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.context.ContextMode
import agent.impl.openai.context.ContextStrategy
import agent.impl.openai.context.branching.BranchManager
import agent.impl.openai.conversation.ConversationRepository
import agent.impl.openai.messages.MessageFactory
import agent.impl.openai.model.Answer
import agent.impl.openai.model.ModelInstruction
import agent.impl.openai.model.ModelReasoningEffort
import agent.impl.openai.model.ModelVersion
import agent.impl.openai.prompt.PromptBuilder
import agent.impl.openai.responseparser.ResponseParser
import agent.impl.openai.statenormalizer.StateNormalizer
import kotlinx.coroutines.sync.withLock
import session.SessionLocks
import store.BranchState

class OpenAIChatAgent(
    private val sessionId: String,
    private val conversationRepository: ConversationRepository,
    private val normalizer: StateNormalizer,
    private val prompts: PromptBuilder,
    private val openai: OpenaiApi,
    private val parser: ResponseParser,
    private val messageFactory: MessageFactory,
    private val model: ModelVersion = ModelVersion.DEFAULT_MODEL_VERSION,
    private val reasoningEffort: ModelReasoningEffort = ModelReasoningEffort.DEFAULT_REASONING_EFFORT,
    private val systemInstruction: ModelInstruction = ModelInstruction.DEFAULT_SYSTEM_INSTRUCTION,
    private val strategies: Map<ContextMode, ContextStrategy>,
    private var mode: ContextMode = ContextMode.SUMMARY,
    private val branchManager: BranchManager = BranchManager()
) : Agent {

    private val mutex = SessionLocks.mutexFor(sessionId)

    override fun setContextMode(newMode: ContextMode) = runBlocking {
        mutex.withLock {
            mode = newMode

            var state = conversationRepository.load(sessionId)
            val strategy = strategies[mode] ?: error("No strategy for mode=$mode")
            state = strategy.onModeActivated(state)

            conversationRepository.save(sessionId, state)
        }
    }

    fun createCheckpoint(checkpointId: String) = runBlocking {
        mutex.withLock {
            var state = conversationRepository.load(sessionId)
            state = branchManager.createCheckpoint(state, checkpointId)
            conversationRepository.save(sessionId, state)
        }
    }

    fun forkBranch(checkpointId: String, newBranchId: String) = runBlocking {
        mutex.withLock {
            var state = conversationRepository.load(sessionId)
            state = branchManager.forkBranch(state, checkpointId, newBranchId)
            conversationRepository.save(sessionId, state)
        }
    }

    fun switchBranch(branchId: String) = runBlocking {
        mutex.withLock {
            var state = conversationRepository.load(sessionId)
            state = branchManager.switchBranch(state, branchId)
            conversationRepository.save(sessionId, state)
        }
    }

    override suspend fun ask(userText: String): Answer = mutex.withLock {
        var state = conversationRepository.load(sessionId)
        state = normalizer.normalize(state)

        // --- ветки: гарантируем текущую ветку ---
        val branches = state.branches.toMutableMap()
        val current = branches[state.currentBranchId] ?: BranchState(
            messages = mutableListOf(),
            checkpoints = mutableMapOf()
        )
        branches[state.currentBranchId] = current
        state = state.copy(branches = branches)

        state = prompts.ensureDeveloper(state, systemInstruction.instruction)

        current.messages.add(messageFactory.msg("user", userText))

        state = state.copy(messages = (state.messages.take(1) + current.messages).toMutableList())

        val strategy = strategies[mode] ?: error("No strategy for mode=$mode")

        state = strategy.onUserMessage(state)

        if (mode == ContextMode.SUMMARY) {
            val head = state.messages.take(1) // developer/system
            val tail = state.messages.drop(1) // то, что compressor оставил

            // синхронизируем текущую ветку с tail после компрессии
            val updatedBranches = state.branches.toMutableMap()
            val updatedCurrent = updatedBranches[state.currentBranchId]
                ?: BranchState(messages = mutableListOf(), checkpoints = mutableMapOf())

            updatedCurrent.messages.clear()
            updatedCurrent.messages.addAll(tail)

            updatedBranches[state.currentBranchId] = updatedCurrent

            // пересобираем view
            state = state.copy(
                branches = updatedBranches,
                messages = (head + updatedCurrent.messages).toMutableList()
            )
        } else {
            // для остальных стратегий можно (опционально) поддерживать view в актуальном состоянии
            val updatedBranches = state.branches.toMutableMap()
            val updatedCurrent = updatedBranches[state.currentBranchId]!!
            state = state.copy(messages = (state.messages.take(1) + updatedCurrent.messages).toMutableList())
        }

        // --- input формирует стратегия ---
        val inputForLLM = strategy.buildInputForLLM(
            state = state,
            systemInstruction = systemInstruction.instruction,
            keepLastN = 12
        )

        val historyTokens = openai.inputTokens(model.version, inputForLLM)

        val requestMap: Map<String, Any> = mapOf(
            "model" to model.version,
            "input" to inputForLLM,
            "reasoning" to mapOf("effort" to reasoningEffort.level, "summary" to "concise")
        )

        val raw = openai.responses(requestMap)
        val parsed = parser.parse(raw)

        // --- assistant -> текущая ветка ---
        val updatedBranches2 = state.branches.toMutableMap()
        val updatedCurrent2 = updatedBranches2[state.currentBranchId]!!
        updatedCurrent2.messages.add(messageFactory.msg("assistant", parsed.reply))
        updatedBranches2[state.currentBranchId] = updatedCurrent2

        // --- view: developer + current branch messages ---
        val newMessagesView = (state.messages.take(1) + updatedCurrent2.messages).toMutableList()
        state = state.copy(branches = updatedBranches2, messages = newMessagesView)

        conversationRepository.save(sessionId, state)

        val stats = parsed.stats?.copy(historyTokens = historyTokens)
        Answer(parsed.reply, stats)
    }

    override suspend fun reset() = mutex.withLock {
        conversationRepository.delete(sessionId)
    }

    override fun getContextMode(): ContextMode {
        return mode
    }
}