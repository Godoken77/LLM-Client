package agent.impl.openai.taskstages.ports

interface TaskMessageFactory {
    fun msg(role: String, text: String): Map<String, Any>
    fun extractText(message: Map<String, Any>): String
}
