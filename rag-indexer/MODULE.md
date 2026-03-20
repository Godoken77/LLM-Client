# rag-indexer

Модуль для индексирования документов в формате RAG (Retrieval-Augmented Generation).

Читает документ с диска → разбивает на чанки → генерирует эмбеддинги через Ollama → сохраняет индекс в JSON.

Индекс предназначен для последующего семантического поиска: при получении запроса вычисляется его эмбеддинг и через cosine-similarity находятся наиболее релевантные чанки.

---

## Структура модуля

```
rag-indexer/
├── build.gradle.kts
└── src/
    ├── main/kotlin/ragindexer/
    │   ├── Main.kt                          # CLI точка входа, парсинг аргументов
    │   ├── model/
    │   │   └── Models.kt                    # ChunkMetadata, Chunk, IndexedChunk, DocumentIndex
    │   ├── reader/
    │   │   └── DocumentReader.kt            # Чтение txt / md / pdf / doc / docx
    │   ├── chunker/
    │   │   ├── Chunker.kt                   # Интерфейс чанкера
    │   │   ├── ChunkingStrategy.kt          # Enum: FIXED_SIZE | STRUCTURAL
    │   │   ├── FixedSizeChunker.kt          # Скользящее окно фиксированного размера
    │   │   └── StructuralChunker.kt         # Разбивка по заголовкам / разделам
    │   ├── embedding/
    │   │   ├── EmbeddingProvider.kt         # Интерфейс провайдера эмбеддингов
    │   │   └── OllamaEmbeddingProvider.kt   # HTTP-клиент к Ollama /api/embeddings
    │   ├── store/
    │   │   ├── IndexStore.kt                # Интерфейс хранилища индексов
    │   │   └── JsonIndexStore.kt            # Файловое JSON-хранилище
    │   └── pipeline/
    │       └── IndexingPipeline.kt          # Оркестратор: read → chunk → embed → save
    └── test/kotlin/ragindexer/
        └── chunker/
            ├── FixedSizeChunkerTest.kt
            └── StructuralChunkerTest.kt
```

### Модели данных

```
ChunkMetadata
  chunkId      – UUID чанка
  source       – абсолютный путь к исходному файлу
  title        – имя файла без расширения
  section      – заголовок раздела (только для STRUCTURAL, иначе null)
  chunkIndex   – порядковый номер чанка в документе (0-based)
  strategy     – "FIXED_SIZE" или "STRUCTURAL"

Chunk
  text         – текст чанка
  metadata     – ChunkMetadata

IndexedChunk
  text         – текст чанка
  embedding    – вектор эмбеддинга (List<Double>)
  metadata     – ChunkMetadata

DocumentIndex  (сохраняется в JSON-файл)
  indexId      – UUID индекса
  source       – абсолютный путь к документу
  title        – имя файла без расширения
  strategy     – "FIXED_SIZE" или "STRUCTURAL"
  model        – название модели Ollama
  createdAt    – ISO-8601 timestamp
  totalChunks  – количество чанков
  chunks       – List<IndexedChunk>
```

---

## Стратегии чанкинга

### FIXED_SIZE

Делит текст скользящим окном фиксированного размера с перекрытием.

```
Параметры по умолчанию:
  chunkSize  = 1000 символов
  overlap    = 200 символов  (шаг = 800 символов)

Позиции окна при тексте в 2500 символов:
  чанк 0 → [0 .. 999]
  чанк 1 → [800 .. 1799]
  чанк 2 → [1600 .. 2499]
```

Перекрытие нужно, чтобы предложения, попавшие на границу, присутствовали в обоих соседних чанках — это повышает вероятность нахождения нужного фрагмента при поиске.

Подходит для: любого документа, когда важна равномерность чанков.

### STRUCTURAL

Разбивает текст по смысловым границам, обнаруженным в тексте.

Порядок приоритетов:
1. **ATX-заголовки** — строки, начинающиеся с `#`, `##`, `###` и т.д.
2. **Setext-заголовки** — строка, за которой следует `===` или `---`
3. **Нумерованные разделы** — строки вида `1. Introduction`, `2.3 Methods`
4. **Разрыв страницы** — символ `\f`, вставляемый PDFBox/POI при извлечении текста
5. **Параграфы** — две и более пустые строки подряд (фолбэк для неструктурированного текста)

Разделы короче `minChunkSize` символов не отбрасываются — они мержатся со следующим разделом.

Подходит для: markdown-документов, научных статей, технических отчётов.

---

## Хранение индексов

```
data/indices/
  fixed_size/
    {indexId}.json
  structural/
    {indexId}.json
```

Каждый файл — полный `DocumentIndex` в pretty-printed JSON. Путь к директории настраивается флагом `--output`.

---

## Сборка и запуск

```bash
# Сборка fat JAR
./gradlew :rag-indexer:shadowJar

# Запуск
java -jar rag-indexer/build/libs/rag-indexer-all.jar --file <path> [options]
```

```
Обязательный аргумент:
  --file <path>              Путь к документу (txt, md, pdf, doc, docx)

Чанкинг:
  --strategy <name>          FIXED_SIZE | STRUCTURAL  (default: FIXED_SIZE)
  --both                     Индексировать обеими стратегиями за один запуск
  --chunk-size <n>           Символов в чанке для FIXED_SIZE      (default: 1000)
  --chunk-overlap <n>        Перекрытие для FIXED_SIZE             (default: 200)
  --min-section-size <n>     Мин. символов в разделе для STRUCTURAL (default: 50)

Ollama:
  --ollama-url <url>         URL инстанса Ollama  (default: http://localhost:11434)
  --model <name>             Модель эмбеддингов   (default: nomic-embed-text)

Вывод:
  --output <dir>             Директория для индексов  (default: data/indices)
```

---

## Примеры

### Пример 1 — TXT-файл, FIXED_SIZE

**Входной файл** `notes.txt`:
```
Kotlin is a modern programming language developed by JetBrains.
It runs on the JVM and is fully interoperable with Java.
Kotlin supports null safety, extension functions, and coroutines.

Coroutines allow writing asynchronous code in a sequential style.
They are lightweight and do not block threads.
The main primitives are launch, async, and Flow.
```

**Команда:**
```bash
java -jar rag-indexer-all.jar --file notes.txt --strategy FIXED_SIZE \
     --chunk-size 150 --chunk-overlap 30
```

**Выходной файл** `data/indices/fixed_size/3f2a1b4c-....json`:
```json
{
  "indexId": "3f2a1b4c-8e91-4d02-bc3a-1234567890ab",
  "source": "/home/user/notes.txt",
  "title": "notes",
  "strategy": "FIXED_SIZE",
  "model": "nomic-embed-text",
  "createdAt": "2026-03-20T10:15:00Z",
  "totalChunks": 3,
  "chunks": [
    {
      "text": "Kotlin is a modern programming language developed by JetBrains.\nIt runs on the JVM and is fully interoperable with Java.\nKotlin supports null safety",
      "embedding": [0.0231, -0.1847, 0.0563, "...767 more values..."],
      "metadata": {
        "chunkId": "a1b2c3d4-...",
        "source": "/home/user/notes.txt",
        "title": "notes",
        "section": null,
        "chunkIndex": 0,
        "strategy": "FIXED_SIZE"
      }
    },
    {
      "text": "null safety, extension functions, and coroutines.\n\nCoroutines allow writing asynchronous code in a sequential style.\nThey are lightweight",
      "embedding": [-0.0912, 0.2341, -0.0087, "..."],
      "metadata": {
        "chunkId": "b2c3d4e5-...",
        "source": "/home/user/notes.txt",
        "title": "notes",
        "section": null,
        "chunkIndex": 1,
        "strategy": "FIXED_SIZE"
      }
    },
    {
      "text": "lightweight and do not block threads.\nThe main primitives are launch, async, and Flow.",
      "embedding": [0.1102, -0.0435, 0.3211, "..."],
      "metadata": {
        "chunkId": "c3d4e5f6-...",
        "source": "/home/user/notes.txt",
        "title": "notes",
        "section": null,
        "chunkIndex": 2,
        "strategy": "FIXED_SIZE"
      }
    }
  ]
}
```

---

### Пример 2 — Markdown-файл, STRUCTURAL

**Входной файл** `guide.md`:
```markdown
# Installation

Download the binary from the releases page and add it to your PATH.
Run `app --version` to verify the installation was successful.

## Linux

Use the package manager: `apt install myapp` or `yum install myapp`.
Configuration files are stored in `/etc/myapp/`.

## Windows

Run the installer `.exe` and follow the setup wizard.
Configuration is stored in `%APPDATA%\myapp\`.

# Configuration

Edit the config file to set connection parameters and timeouts.
All values can also be passed as environment variables.
```

**Команда:**
```bash
java -jar rag-indexer-all.jar --file guide.md --strategy STRUCTURAL
```

**Выходной файл** `data/indices/structural/7d8e9f0a-....json`:
```json
{
  "indexId": "7d8e9f0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a",
  "source": "/home/user/guide.md",
  "title": "guide",
  "strategy": "STRUCTURAL",
  "model": "nomic-embed-text",
  "createdAt": "2026-03-20T10:22:00Z",
  "totalChunks": 4,
  "chunks": [
    {
      "text": "Download the binary from the releases page and add it to your PATH.\nRun `app --version` to verify the installation was successful.",
      "embedding": [0.0871, -0.2103, 0.1456, "..."],
      "metadata": {
        "chunkId": "d4e5f6a7-...",
        "source": "/home/user/guide.md",
        "title": "guide",
        "section": "Installation",
        "chunkIndex": 0,
        "strategy": "STRUCTURAL"
      }
    },
    {
      "text": "Use the package manager: `apt install myapp` or `yum install myapp`.\nConfiguration files are stored in `/etc/myapp/`.",
      "embedding": [-0.0543, 0.1987, 0.0234, "..."],
      "metadata": {
        "chunkId": "e5f6a7b8-...",
        "source": "/home/user/guide.md",
        "title": "guide",
        "section": "Linux",
        "chunkIndex": 1,
        "strategy": "STRUCTURAL"
      }
    },
    {
      "text": "Run the installer `.exe` and follow the setup wizard.\nConfiguration is stored in `%APPDATA%\\myapp\\`.",
      "embedding": [0.1123, -0.0876, 0.2341, "..."],
      "metadata": {
        "chunkId": "f6a7b8c9-...",
        "source": "/home/user/guide.md",
        "title": "guide",
        "section": "Windows",
        "chunkIndex": 2,
        "strategy": "STRUCTURAL"
      }
    },
    {
      "text": "Edit the config file to set connection parameters and timeouts.\nAll values can also be passed as environment variables.",
      "embedding": [-0.0312, 0.1654, -0.0987, "..."],
      "metadata": {
        "chunkId": "a7b8c9d0-...",
        "source": "/home/user/guide.md",
        "title": "guide",
        "section": "Configuration",
        "chunkIndex": 3,
        "strategy": "STRUCTURAL"
      }
    }
  ]
}
```

Ключевое отличие от FIXED_SIZE: поле `section` заполнено заголовком (`"Installation"`, `"Linux"`, etc.), что позволяет при поиске фильтровать результаты по разделу.

---

### Пример 3 — PDF, обе стратегии

```bash
java -jar rag-indexer-all.jar --file report.pdf --both --output /data/indexes
```

Вывод в консоль:
```
Indexing with BOTH strategies...

[Pipeline] ─── Starting indexing ───────────────────────────────
[Pipeline] File     : /home/user/report.pdf
[Pipeline] Strategy : FIXED_SIZE
[Pipeline] Reading document...
[Pipeline] Extracted 18432 characters
[Pipeline] Generated 24 chunks
[Pipeline] Embedding via Ollama (model: nomic-embed-text)...
[Pipeline]   chunk 24/24
[Pipeline] Embeddings done (24 vectors)
[IndexStore] Saved: /data/indexes/fixed_size/c1d2e3f4-....json  (24 chunks)
[Pipeline] ─── Indexing complete ──────────────────────────────
[Pipeline] Index ID : c1d2e3f4-9a8b-7c6d-5e4f-3a2b1c0d9e8f
[Pipeline] Chunks   : 24

[Pipeline] ─── Starting indexing ───────────────────────────────
[Pipeline] File     : /home/user/report.pdf
[Pipeline] Strategy : STRUCTURAL
...
[IndexStore] Saved: /data/indexes/structural/f9e8d7c6-....json  (11 chunks)

Done.
  FIXED_SIZE  index ID : c1d2e3f4-...  (24 chunks)
  STRUCTURAL  index ID : f9e8d7c6-...  (11 chunks)
```

Файловая структура после запуска:
```
/data/indexes/
  fixed_size/
    c1d2e3f4-9a8b-7c6d-5e4f-3a2b1c0d9e8f.json   # 24 чанка
  structural/
    f9e8d7c6-5b4a-3c2d-1e0f-9a8b7c6d5e4f.json   # 11 чанков (по разделам)
```

---

## Расширение в следующих итерациях

`IndexStore` спроектирован с прицелом на добавление поиска. Следующий шаг — реализовать `SearchableIndexStore`:

```kotlin
interface SearchableIndexStore : IndexStore {
    // Загрузить все индексы в память, посчитать cosine-similarity с queryEmbedding
    fun search(queryEmbedding: List<Double>, topK: Int = 5): List<ScoredChunk>
}

data class ScoredChunk(
    val chunk: IndexedChunk,
    val score: Double   // cosine similarity [0..1]
)
```

Поскольку все чанки уже хранят поле `embedding`, схему менять не нужно — достаточно добавить реализацию поиска.
