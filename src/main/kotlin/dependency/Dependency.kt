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
import agent.impl.openai.compsessor.ConversationCompressorImpl
import agent.impl.openai.context.ContextMode
import agent.impl.openai.context.ContextStrategy
import agent.impl.openai.context.slider.SlidingWindowStrategy
import agent.impl.openai.context.sticky.FactsUpdaterImpl
import agent.impl.openai.context.sticky.StickyFactsStrategy
import agent.impl.openai.context.summary.SummaryStrategy
import agent.impl.openai.conversation.ConversationRepositoryImpl
import agent.impl.openai.messages.MessageFactoryImpl
import agent.impl.openai.prompt.PromptBuilderImpl
import agent.impl.openai.responseparser.GsonResponseParserImpl
import agent.impl.openai.statenormalizer.StateNormalizerImpl
import agent.impl.openai.summarizer.LlmSummarizer
import dependency.Dependency.httpClient
import store.ConversationStore
import store.JsonConversationStore
import store.impl.AgentStore
import java.nio.file.Paths

object Dependency {
    val conversationStore: ConversationStore = JsonConversationStore(Paths.get("data"))

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

    val agentStore = AgentStore(
       dependency = OpenaiDependency
    )

    fun close() = httpClient.close()
}

object OpenaiDependency {

    val openaiApi: OpenaiApi by lazy {
        OpenAIClient(
            http = httpClient,
            apiKey = System.getenv("OPENAI_API_KEY") ?:  error("Open API key not found")
        )
    }

    val msgFactory = MessageFactoryImpl()
    val normalizer = StateNormalizerImpl(msgFactory)
    val prompts = PromptBuilderImpl(msgFactory)
    val parser = GsonResponseParserImpl()

    val conversationRepository = ConversationRepositoryImpl(Dependency.conversationStore)

    private val summarizer = LlmSummarizer(
        openai = openaiApi,
        responseParser = parser,
        messages = msgFactory
    )

    val compressor = ConversationCompressorImpl(
        keepLastN = 12,
        chunkSize = 10,
        summarizer = summarizer
    )

    val factsUpdater = FactsUpdaterImpl(
        openai = openaiApi,
        mf = msgFactory
    )

    val strategies: Map<ContextMode, ContextStrategy> = mapOf(
        Pair(
            ContextMode.SLIDING_WINDOW,
            SlidingWindowStrategy(
                msgFactory
            )
        ),
        Pair(
            ContextMode.STICKY_FACTS,
            StickyFactsStrategy(
                msgFactory,
                factsUpdater = factsUpdater
            )
        ),
        Pair(
            ContextMode.SUMMARY,
            SummaryStrategy(
                compressor = compressor,
                prompts = prompts
            )
        )
    )
}