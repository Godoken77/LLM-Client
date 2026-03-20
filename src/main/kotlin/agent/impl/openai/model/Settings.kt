package agent.impl.openai.model

enum class ModelVersion(val version: String) {
    DEFAULT_MODEL_VERSION("gpt-5.2")
}

enum class ModelInstruction(val instruction: String) {
    DEFAULT_SYSTEM_INSTRUCTION("Ты полезный ассистент. Отвечай по-русски, кратко и по делу.")
}

enum class ModelReasoningEffort(val level: String) {
    DEFAULT_REASONING_EFFORT("high")
}
