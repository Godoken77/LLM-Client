# agent-memory

Независимый Gradle-модуль: хранит всю логику памяти агента.
Не зависит от основного модуля — связь через port-интерфейсы.

---

## Структура

```
agent-memory/
└── src/main/kotlin/
    ├── agent/impl/openai/
    │   ├── memory/
    │   │   ├── ports/                    # 4 port-интерфейса (см. ниже)
    │   │   ├── engine/
    │   │   │   └── MemoryEngine.kt       # публичный интерфейс движка памяти
    │   │   └── layers/                   # единственная архитектура памяти
    │   │       ├── engine/               # MemoryLayersEngine
    │   │       ├── model/                # MemoryState (short/working/long-term)
    │   │       ├── prompt/               # MemoryPromptBuilder
    │   │       ├── repository/           # MemoryRepository + FileMemoryRepository
    │   │       ├── router/               # MemoryRouter + MemoryRouterImpl
    │   │       └── updater/              # WorkingMemoryUpdater, LongTermMemoryUpdater
    │   └── taskstages/                   # TaskStateMachine встроена в WorkingMemory
    │       ├── Actions.kt                # ExpectedAction enum
    │       ├── Stages.kt                 # TaskStage enum
    │       ├── TaskStateMachine.kt       # data class состояния задачи
    │       ├── TaskTransition.kt         # enum переходов + TaskTransitionDecision
    │       ├── service/                  # TaskStateMachineService (детерминированный)
    │       └── stateupdater/             # TaskStateUpdater (LLM-driven)
```

---

## Архитектура памяти: Memory Layers

Трёхуровневая память: краткосрочная → рабочая → долгосрочная.

| Уровень | Хранит | Обновляется |
|---|---|---|
| **Short-term** | Последние N сообщений сессии | Каждое сообщение |
| **Working** | Текущая задача + `TaskStateMachine` | LLM после каждого хода пользователя |
| **Long-term** | Факты, решения, знания о пользователе | LLM после каждого хода пользователя |

Маршрутизатор (`MemoryRouter`) запускает `WorkingMemoryUpdater` и `LongTermMemoryUpdater` на каждое сообщение пользователя, `MemoryLayersEngine` сохраняет результат в `MemoryRepository`.

---

## TaskStateMachine

Конечный автомат задачи встроен в рабочую память:

```
PLANNING → EXECUTION → VALIDATION → DONE
             ↑↓ PAUSED
```

`TaskStateUpdaterImpl` анализирует последние 10 сообщений через LLM и определяет переход (`START`, `APPROVE_PLAN`, `FINISH_EXECUTION`, `VALIDATION_OK/FAIL`, `PAUSE`, `RESUME`, `NO_CHANGE`). `TaskStateMachineService` применяет его детерминированно с проверкой допустимости.

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

---

## Публичный интерфейс

```kotlin
interface MemoryEngine {
    suspend fun onModeActivated()
    suspend fun buildInput(userText: String): List<Map<String, Any>>
    suspend fun saveToolMessages(messages: List<Map<String, Any>>)
    suspend fun saveAssistantReply(reply: String)
    suspend fun reset()
}
```

`buildInput()` — строит список сообщений для LLM: системная инструкция + профиль пользователя + инварианты + состояние задачи + долгосрочная память + краткосрочные сообщения.

---

## Подключение (пример из `Dependency.kt`)

```kotlin
val llmClientAdapter = OpenaiMemoryLlmClient(openaiApi)
val messageFactoryAdapter = MessageFactoryAdapter(messageFactory)
val userProfileServiceAdapter = UserProfileServiceAdapter(profileRepository, personalizationService)
val invariantServiceAdapter = InvariantServiceAdapter(invariantRepository, invariantPromptBuilder)

val layersEngine = MemoryLayersEngine(
    memoryRepository = FileMemoryRepository(File("./data")),
    memoryRouter = MemoryRouterImpl(
        messageFactory = messageFactoryAdapter,
        workingMemoryUpdater = WorkingMemoryUpdaterImpl(llmClientAdapter, messageFactoryAdapter, taskStateUpdater),
        longTermMemoryUpdater = LongTermMemoryUpdaterImpl(llmClientAdapter, messageFactoryAdapter)
    ),
    promptBuilder = MemoryPromptBuilder(messageFactoryAdapter, userProfileServiceAdapter, invariantServiceAdapter),
    systemInstruction = "Ты полезный ассистент.",
    keepLastN = 12,
    sessionId = sessionId
)
```
