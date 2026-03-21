package dependency

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.serialization.gson.gson
import agent.impl.openai.api.OpenAIClient
import agent.impl.openai.api.OpenaiApi
import agent.impl.openai.memory.layers.engine.MemoryLayersEngine
import agent.impl.openai.memory.layers.prompt.MemoryPromptBuilder
import agent.impl.openai.memory.layers.repository.FileMemoryRepository
import agent.impl.openai.memory.layers.repository.MemoryRepository
import agent.impl.openai.memory.layers.router.MemoryRouter
import agent.impl.openai.memory.layers.router.MemoryRouterImpl
import agent.impl.openai.memory.layers.updater.longterm.LongTermMemoryUpdaterImpl
import agent.impl.openai.memory.layers.updater.workterm.WorkingMemoryUpdaterImpl
import agent.impl.openai.memory.adapters.InvariantServiceAdapter
import agent.impl.openai.memory.adapters.MessageFactoryAdapter
import agent.impl.openai.memory.adapters.OpenaiMemoryLlmClient
import agent.impl.openai.memory.adapters.UserProfileServiceAdapter
import agent.impl.openai.taskstages.adapters.TaskLlmClientAdapter
import agent.impl.openai.taskstages.adapters.TaskMessageFactoryAdapter
import agent.impl.openai.invariants.AssistantInvariant
import agent.impl.openai.invariants.InvariantSet
import agent.impl.openai.invariants.InvariantSeverity
import agent.impl.openai.invariants.InvariantType
import agent.impl.openai.invariants.invariantchecker.RuleBasedInvariantChecker
import agent.impl.openai.invariants.prompt.InvariantPromptBuilderImpl
import agent.impl.openai.invariants.refusalbuilder.InvariantRefusalBuilderImpl
import agent.impl.openai.invariants.repository.FileInvariantRepository
import agent.impl.openai.messages.MessageFactoryImpl
import agent.impl.openai.model.ModelInstruction
import agent.impl.openai.responseparser.GsonResponseParserImpl
import agent.impl.openai.taskstages.service.TaskStateMachineServiceImpl
import agent.impl.openai.taskstages.stateupdater.TaskStateUpdaterImpl
import agent.impl.openai.userprofile.FileUserProfileRepository
import agent.impl.openai.userprofile.service.PersonalizationService
import agent.impl.openai.userprofile.service.PersonalizationServiceImpl
import dependency.Dependency.httpClient
import dependency.Dependency.sessionId
import agent.impl.openai.tools.McpToolProviderImpl
import agent.impl.openai.tools.PipelinedToolAwareExecutorImpl
import agent.impl.openai.tools.ToolAwareOpenaiExecutor
import mcp.McpClient
import mcp.StdioMcpClient
import store.ConversationStore
import store.JsonConversationStore
import store.SessionId
import store.impl.AgentStore
import java.io.File
import java.nio.file.Paths
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

object Dependency {

    @OptIn(ExperimentalAtomicApi::class)
    val sessionId: AtomicReference<SessionId> = AtomicReference("")

    val conversationStore: ConversationStore = JsonConversationStore(Paths.get("data"))

    val mcpClient: McpClient by lazy {
        val jar = File("mcp-api-server/build/libs")
            .listFiles { f -> f.name.endsWith("-all.jar") }
            ?.firstOrNull()
            ?: error("mcp-api-server JAR not found — run ./gradlew :mcp-api-server:shadowJar first")
        StdioMcpClient(serverCommand = listOf("java", "-jar", jar.path))
    }

    val notesClient: McpClient by lazy {
        val jar = File("mcp-notes-server/build/libs")
            .listFiles { f -> f.name.endsWith("-all.jar") }
            ?.firstOrNull()
            ?: error("mcp-notes-server JAR not found — run ./gradlew :mcp-notes-server:shadowJar first")
        StdioMcpClient(serverCommand = listOf("java", "-jar", jar.path))
    }

    val weatherClient: McpClient by lazy {
        val jar = File("mcp-weather-server/build/libs")
            .listFiles { f -> f.name.endsWith("-all.jar") }
            ?.firstOrNull()
            ?: error("mcp-weather-server JAR not found — run ./gradlew :mcp-weather-server:shadowJar first")
        StdioMcpClient(serverCommand = listOf("java", "-jar", jar.path))
    }

    val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { gson() }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 90_000
                requestTimeoutMillis = 120_000
            }
            defaultRequest { accept(ContentType.Application.Json) }
        }
    }

    val executor: ToolAwareOpenaiExecutor by lazy {
        PipelinedToolAwareExecutorImpl(
            openai = OpenaiDependency.openaiApi,
            parser = OpenaiDependency.parser,
            stages = listOf(
                McpToolProviderImpl(mcpClient),    // Stage 1: scheduler
                McpToolProviderImpl(notesClient),  // Stage 2: notes
                McpToolProviderImpl(weatherClient) // Stage 3: weather
            )
        )
    }

    @OptIn(ExperimentalAtomicApi::class)
    val agentStore = AgentStore(
       dependency = OpenaiDependency,
       sessionId = sessionId.load(),
       executor = executor
    )

    fun close() = httpClient.close()
}

object OpenaiDependency {

    val openaiApi: OpenaiApi by lazy {
        OpenAIClient(
            http = httpClient,
            apiKey = System.getenv("OPENAI_API_KEY") ?: error("Open API key not found")
        )
    }

    val messageFactory = MessageFactoryImpl()
    val parser = GsonResponseParserImpl()

    private val profileRepository = FileUserProfileRepository()
    private val personalizationService: PersonalizationService = PersonalizationServiceImpl()

    // Adapters — bridge main implementations to agent-memory port interfaces
    val llmClientAdapter = OpenaiMemoryLlmClient(openaiApi)
    val messageFactoryAdapter = MessageFactoryAdapter(messageFactory)
    val userProfileServiceAdapter = UserProfileServiceAdapter(profileRepository, personalizationService)

    val taskStateMachineService = TaskStateMachineServiceImpl()

    val taskLlmClientAdapter = TaskLlmClientAdapter(openaiApi)
    val taskMessageFactoryAdapter = TaskMessageFactoryAdapter(messageFactory)

    val taskStateUpdater = TaskStateUpdaterImpl(
        llmClient = taskLlmClientAdapter,
        messageFactory = taskMessageFactoryAdapter,
        stateMachineService = taskStateMachineService
    )

    val workingMemoryUpdater = WorkingMemoryUpdaterImpl(
        llmClient = llmClientAdapter,
        messageFactory = messageFactoryAdapter,
        taskStateUpdater = taskStateUpdater
    )

    val longTermMemoryUpdater = LongTermMemoryUpdaterImpl(
        llmClient = llmClientAdapter,
        messageFactory = messageFactoryAdapter
    )

    val memoryRepository: MemoryRepository = FileMemoryRepository(File("./data"))

    val memoryRouter: MemoryRouter = MemoryRouterImpl(
        messageFactory = messageFactoryAdapter,
        workingMemoryUpdater = workingMemoryUpdater,
        longTermMemoryUpdater = longTermMemoryUpdater,
    )

    val invariantRepository = FileInvariantRepository()
    val invariantChecker = RuleBasedInvariantChecker()
    val invariantPromptBuilder = InvariantPromptBuilderImpl()
    val invariantServiceAdapter = InvariantServiceAdapter(invariantRepository, invariantPromptBuilder)
    val invariantRefusalBuilder = InvariantRefusalBuilderImpl()

    val memoryPromptBuilder = MemoryPromptBuilder(
        messageFactory = messageFactoryAdapter,
        userProfileService = userProfileServiceAdapter,
        invariantService = invariantServiceAdapter
    )

    @OptIn(ExperimentalAtomicApi::class)
    val layersEngine = MemoryLayersEngine(
        memoryRepository = memoryRepository,
        memoryRouter = memoryRouter,
        promptBuilder = memoryPromptBuilder,
        systemInstruction = ModelInstruction.DEFAULT_SYSTEM_INSTRUCTION.instruction,
        keepLastN = 12,
        sessionId = sessionId.load(),
    )

    val invariants = InvariantSet(
        mutableListOf(
            AssistantInvariant(
                id = "arch-1",
                type = InvariantType.ARCHITECTURE,
                title = "Сохранять выбранную архитектуру",
                description = "Не предлагать решения, которые ломают текущую архитектуру приложения",
                severity = InvariantSeverity.HARD
            ),
            AssistantInvariant(
                id = "tech-1",
                type = InvariantType.TECH_DECISION,
                title = "Использовать Ktor вместо OkHttp",
                description = "Не использовать OkHttp, использовать только Ktor для HTTP-клиента",
                severity = InvariantSeverity.HARD
            ),
            AssistantInvariant(
                id = "stack-1",
                type = InvariantType.STACK_CONSTRAINT,
                title = "Без branching",
                description = "Не использовать branching в реализации управления контекстом",
                severity = InvariantSeverity.HARD
            ),
            AssistantInvariant(
                id = "biz-1",
                type = InvariantType.BUSINESS_RULE,
                title = "Не менять публичные интерфейсы без причины",
                description = "Не предлагать изменения публичных интерфейсов, если это не требуется явно",
                severity = InvariantSeverity.HARD
            )
        )
    )
}
