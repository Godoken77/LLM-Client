# mcp-api-server

MCP-сервер для планирования и выполнения задач по расписанию. Работает как дочерний процесс главного приложения через stdio-транспорт MCP.

Позволяет LLM создавать одиночные и периодические задачи, записывать произвольные точки данных и получать сводку по всем задачам.

---

## Структура модуля

```
mcp-api-server/
├── build.gradle.kts
└── src/
    ├── main/kotlin/mcpserver/
    │   ├── Main.kt                      # Точка входа: инициализация MCP-сервера
    │   ├── model/
    │   │   └── ScheduledTask.kt         # TaskType, TaskResult, ScheduledTask
    │   ├── scheduler/
    │   │   └── TaskScheduler.kt         # Фоновый тикер (каждые 5 сек), запуск задач
    │   ├── store/
    │   │   └── TaskStore.kt             # Mutex-safe CRUD + JSON-персистентность
    │   └── tools/
    │       └── SchedulerTools.kt        # 7 MCP-инструментов
    └── test/kotlin/mcpserver/
        └── SchedulerTest.kt             # 13 unit-тестов
```

### Модели данных

```
TaskType (enum)
  ONCE      – выполняется один раз, затем отключается
  PERIODIC  – повторяется с фиксированным интервалом

TaskResult
  timestamp : Long    – время записи (Unix ms)
  data      : String  – произвольный результат

ScheduledTask
  id              : String         – UUID
  name            : String         – отображаемое имя
  description     : String         – описание / сообщение
  type            : TaskType
  intervalSeconds : Long?          – null для ONCE, обязателен для PERIODIC
  nextRunAt       : Long           – время следующего срабатывания (Unix ms)
  lastRunAt       : Long?          – время последнего срабатывания
  enabled         : Boolean        – false = задача не выполняется
  results         : List<TaskResult> – максимум 100 записей (oldest dropped)
```

### Персистентность

```
data/scheduler/scheduled_tasks.json   – JSON-массив всех задач
```

---

## MCP-инструменты

### `schedule_reminder`
Одиночное напоминание через N секунд.

| Параметр        | Тип    | Обязателен | Описание            |
|-----------------|--------|------------|---------------------|
| `name`          | string | да         | Название            |
| `message`       | string | да         | Текст напоминания   |
| `delay_seconds` | number | да         | Задержка в секундах |

---

### `schedule_periodic`
Периодическая задача с фиксированным интервалом.

| Параметр           | Тип    | Обязателен | Описание                   |
|--------------------|--------|------------|----------------------------|
| `name`             | string | да         | Название                   |
| `description`      | string | да         | Описание                   |
| `interval_seconds` | number | да         | Интервал (минимум 1 сек)   |

---

### `record_data_point`
Вручную добавить точку данных к задаче.

| Параметр  | Тип    | Обязателен | Описание  |
|-----------|--------|------------|-----------|
| `task_id` | string | да         | UUID      |
| `data`    | string | да         | Данные    |

---

### `list_scheduled_tasks`
Список всех задач со статусами. Параметров нет.

---

### `get_task_results`
Последние результаты одной задачи.

| Параметр  | Тип    | Обязателен | Описание                            |
|-----------|--------|------------|-------------------------------------|
| `task_id` | string | да         | UUID                                |
| `limit`   | number | нет        | Количество результатов (default 10) |

---

### `get_summary`
Сводка по всем задачам и накопленным данным. Параметров нет.

---

### `cancel_task`
Отключить задачу (не удаляет, только `enabled=false`).

| Параметр  | Тип    | Обязателен | Описание |
|-----------|--------|------------|----------|
| `task_id` | string | да         | UUID     |

---

## Примеры

### Пример 1 — одиночное напоминание

**Входные данные:**
```json
{
  "tool": "schedule_reminder",
  "arguments": { "name": "Обед", "message": "Время обедать", "delay_seconds": 3600 }
}
```

**Выходные данные:**
```
Reminder 'Обед' scheduled. id=550e8400-e29b-41d4-a716-446655440000, fires at 2026-03-20T14:30:00.123Z
```

---

### Пример 2 — периодический мониторинг

**Входные данные:**
```json
{
  "tool": "schedule_periodic",
  "arguments": { "name": "Heartbeat", "description": "Проверка состояния", "interval_seconds": 60 }
}
```

**Выходные данные:**
```
Periodic task 'Heartbeat' created. id=abc12345-def6-7890-abcd-ef1234567890, interval=60s, first run in 60s
```

После двух тиков — `get_task_results`:
```
=== Heartbeat (PERIODIC) ===
Description : Проверка состояния
Enabled     : true
Total stored: 2 (showing last 10)
Interval    : 60s
Last run    : 2026-03-20T13:31:45.123Z
--- Results ---
  [2026-03-20T13:30:45.123Z] Executed at 2026-03-20T13:30:45.123Z
  [2026-03-20T13:31:45.456Z] Executed at 2026-03-20T13:31:45.456Z
```

---

### Пример 3 — список задач

**Входные данные:**
```json
{ "tool": "list_scheduled_tasks", "arguments": {} }
```

**Выходные данные:**
```
[550e8400] Обед      | ONCE     | ACTIVE   | next in 3540s | results=0  | Время обедать
[abc12345] Heartbeat | PERIODIC | ACTIVE   | next in 15s   | results=2  | Проверка состояния
```

---

### Пример 4 — сводка

**Входные данные:**
```json
{ "tool": "get_summary", "arguments": {} }
```

**Выходные данные:**
```
=== Scheduler Summary ===
Generated   : 2026-03-20T13:32:00.000Z
Total tasks : 2 (active=2, disabled=0)
Total points: 2

[550e8400] Обед [ACTIVE / ONCE]
  Время обедать
  Next run : in 3540s
  Points   : 0

[abc12345] Heartbeat [ACTIVE / PERIODIC]
  Проверка состояния
  Interval : 60s (1min)
  Next run : in 15s
  Last run : 2026-03-20T13:31:45.456Z
  Points   : 2
  Latest   : Executed at 2026-03-20T13:31:45.456Z
```

---

## Сборка и запуск

```bash
# Сборка fat JAR (запускается автоматически при сборке root-модуля)
./gradlew :mcp-api-server:shadowJar

# Сервер запускается главным приложением через ProcessBuilder (stdio)
# Ручной запуск для тестирования:
java -jar mcp-api-server/build/libs/mcp-api-server-all.jar
```

## Ключевые детали реализации

- **Тикер**: проверяет задачи каждые **5 секунд**; не использует cron-библиотеки
- **ONCE**: после срабатывания переводится в `enabled=false`
- **PERIODIC**: после срабатывания пересчитывает `nextRunAt = now + intervalSeconds * 1000`
- **Results cap**: максимум 100 записей на задачу; старые удаляются
- **Thread safety**: все операции `TaskStore` защищены `Mutex`
