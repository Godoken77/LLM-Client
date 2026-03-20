# agent-memory

Независимый Gradle-модуль: хранит всю логику памяти агента.
Не зависит от основного модуля — связь через port-интерфейсы.

---

## Структура

```
agent-memory/
└── src/main/kotlin/
    ├── store/
    │   └── ConversationState.kt          # Messages typealias + ConversationState data class
    ├── agent/impl/openai/
    │   ├── model/
    │   │   └── Settings.kt               # ModelVersion, ModelInstruction, ModelReasoningEffort
    │   ├── agentImpl/
    │   │   └── AgentMemoryMode.kt        # CONTEXT_MODE | MEMORY_LAYERS
    │   ├── conversation/
    │   │   └── ConversationRepository.kt # port-интерфейс (impl в main)
    │   ├── memory/
    │   │   ├── ports/                    # 7 port-интерфейсов (см. ниже)
    │   │   ├── engine/
    │   │   │   └── MemoryEngine.kt       # общий интерфейс движка памяти
    │   │   ├── context/                  # архитектура Context Mode
    │   │   │   ├── ContextStrategy.kt    # интерфейс стратегии + enum ContextMode
    │   │   │   ├── engine/               # ContextModeMemoryEngine
    │   │   │   ├── slider/               # SlidingWindowStrategy
    │   │   │   ├── sticky/               # StickyFactsStrategy + FactsUpdater
    │   │   │   └── summary/              # SummaryStrategy
    │   │   └── layers/                   # архитектура Memory Layers
    │   │       ├── engine/               # MemoryLayersEngine
    │   │       ├── model/                # MemoryState (short/working/long-term)
    │   │       ├── prompt/               # MemoryPromptBuilder
    │   │       ├── repository/           # MemoryRepository + FileMemoryRepository
    │   │       ├── router/               # MemoryRouter + MemoryRouterImpl
    │   │       └── updater/              # WorkingMemoryUpdater, LongTermMemoryUpdater
    │   └── taskstages/                   # TaskStateMachine встроена в WorkingMemory
    │       ├── Actions.kt                # ExpectedAction enum
    │       ├── Stages.kt                 # TaskStage enum
    │       ├── TaskStateMachine.kt       # data class
    │       ├── TaskTransition.kt         # enum переходов
    │       ├── service/                  # TaskStateMachineService
    │       └── stateupdater/             # TaskStateUpdater (LLM-driven)
```

---

## Две архитектуры памяти

### Context Mode (`ContextModeMemoryEngine`)

Управляет `ConversationState` через сменяемую стратегию.

| Стратегия | Описание |
|---|---|
| `SLIDING_WINDOW` | Скользящее окно последних N сообщений |
| `STICKY_FACTS` | LLM извлекает и фиксирует факты из диалога |
| `SUMMARY` | Компрессия старых сообщений в summary |

Переключение режима: `engine.changeMode(ContextMode.SUMMARY)`.

### Memory Layers (`MemoryLayersEngine`)

Трёхуровневая память: краткосрочная → рабочая → долгосрочная.

- **Short-term**: последние N сообщений сессии
- **Working**: текущая задача + `TaskStateMachine`
- **Long-term**: персистентные факты о пользователе (JSON-файл)

Маршрутизатор (`MemoryRouter`) обновляет рабочую и долгосрочную память после каждого сообщения пользователя.

---

## Port-интерфейсы

Всё, что нужно из основного модуля, объявлено как тонкий интерфейс в `memory/ports/`.
Основной модуль реализует их через адаптеры в `memory/adapters/`.

| Port | Реализация в main |
|---|---|
| `MemoryLlmClient` | `OpenaiMemoryLlmClient(OpenaiApi)` |
| `MemoryMessageFactory` | `MessageFactoryAdapter(MessageFactory)` |
| `MemoryUserProfileService` | `UserProfileServiceAdapter(repo, service)` |
| `MemoryInvariantService` | `InvariantServiceAdapter(repo, builder)` |
| `MemoryConversationCompressor` | `CompressorAdapter(ConversationCompressor)` |
| `MemoryStateNormalizer` | `StateNormalizerAdapter(StateNormalizer)` |
| `MemoryContextPromptBuilder` | `PromptBuilderAdapter(PromptBuilder)` |

---

## Подключение (пример из `Dependency.kt`)

```kotlin
// Создать адаптеры
val llmClientAdapter = OpenaiMemoryLlmClient(openaiApi)
val messageFactoryAdapter = MessageFactoryAdapter(messageFactory)
val userProfileServiceAdapter = UserProfileServiceAdapter(profileRepository, personalizationService)

// Context Mode
val contextEngine = ContextModeMemoryEngine(
    sessionId = sessionId,
    conversationRepository = conversationRepository,
    normalizer = StateNormalizerAdapter(normalizer),
    prompts = PromptBuilderAdapter(prompts),
    messageFactory = messageFactoryAdapter,
    strategies = strategies,
    systemInstruction = ModelInstruction.DEFAULT_SYSTEM_INSTRUCTION,
    mode = ContextMode.SUMMARY,
    keepLastN = 12
)

// Memory Layers
val layersEngine = MemoryLayersEngine(
    memoryRepository = FileMemoryRepository(File("./data")),
    memoryRouter = MemoryRouterImpl(messageFactoryAdapter, workingMemoryUpdater, longTermMemoryUpdater),
    promptBuilder = MemoryPromptBuilder(messageFactoryAdapter, userProfileServiceAdapter, invariantServiceAdapter),
    systemInstruction = ModelInstruction.DEFAULT_SYSTEM_INSTRUCTION,
    keepLastN = 12,
    sessionId = sessionId
)
```

---

## Переключение режима памяти агента

`AgentMemoryMode.CONTEXT_MODE` — использует `ContextModeMemoryEngine`.
`AgentMemoryMode.MEMORY_LAYERS` — использует `MemoryLayersEngine`.

Режим задаётся при создании агента через `OpenAIChatAgent(memoryMode = ...)`.
