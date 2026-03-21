# agent-tasks

Независимый Gradle-модуль: управление жизненным циклом задачи агента.
Не зависит от `agent-memory` и основного модуля — связь через собственные port-интерфейсы.

---

## Структура

```
agent-tasks/
└── src/main/kotlin/agent/impl/openai/taskstages/
    ├── ports/
    │   ├── TaskLlmClient.kt       # port: HTTP к LLM
    │   └── TaskMessageFactory.kt  # port: создание/чтение сообщений
    ├── TaskStateMachine.kt        # data class состояния задачи
    ├── Stages.kt                  # TaskStage enum + allowedTransitions
    ├── Actions.kt                 # ExpectedAction enum
    ├── TaskTransition.kt          # TaskTransition enum + Decision + Exception
    ├── service/
    │   └── TaskStateMachineService.kt   # детерминированные переходы
    └── stateupdater/
        └── TaskStateUpdater.kt          # LLM-driven определение перехода
```

---

## Конечный автомат задачи

```
PLANNING → EXECUTION → VALIDATION → DONE
             ↑↓ PAUSED
```

| Стадия | Ожидаемое действие |
|---|---|
| `PLANNING` | `DEFINE_PLAN` |
| `EXECUTION` | `APPLY_CHANGE` |
| `VALIDATION` | `RUN_CHECK` |
| `DONE` | `NONE` |
| `PAUSED` | `WAIT_FOR_USER` |

**Переходы:** `START`, `APPROVE_PLAN`, `FINISH_EXECUTION`, `VALIDATION_OK`, `VALIDATION_FAIL`, `PAUSE`, `RESUME`, `NO_CHANGE`.

`TaskStateMachineServiceImpl` — детерминированный: проверяет допустимость перехода и применяет его. Бросает `InvalidTaskTransitionException` при нарушении.

`TaskStateUpdaterImpl` — LLM-driven: анализирует последние 10 сообщений диалога, определяет переход, делегирует применение сервису. При ошибке парсинга или недопустимом переходе возвращает текущее состояние без изменений.

---

## Port-интерфейсы

| Port | Реализация в main |
|---|---|
| `TaskLlmClient` | `TaskLlmClientAdapter(OpenaiApi)` |
| `TaskMessageFactory` | `TaskMessageFactoryAdapter(MessageFactory)` |

---

## Зависимости модулей

```
main → agent-memory, agent-tasks
agent-memory → agent-tasks   (TaskStateMachine в MemoryState, TaskStateUpdater в WorkingMemoryUpdater)
agent-tasks → (нет зависимостей на другие модули проекта)
```

---

## Подключение (пример из `Dependency.kt`)

```kotlin
val taskLlmClientAdapter = TaskLlmClientAdapter(openaiApi)
val taskMessageFactoryAdapter = TaskMessageFactoryAdapter(messageFactory)

val taskStateUpdater = TaskStateUpdaterImpl(
    llmClient = taskLlmClientAdapter,
    messageFactory = taskMessageFactoryAdapter,
    stateMachineService = TaskStateMachineServiceImpl()
)

// Передаётся в WorkingMemoryUpdaterImpl (agent-memory)
val workingMemoryUpdater = WorkingMemoryUpdaterImpl(
    llmClient = ...,
    messageFactory = ...,
    taskStateUpdater = taskStateUpdater
)
```
