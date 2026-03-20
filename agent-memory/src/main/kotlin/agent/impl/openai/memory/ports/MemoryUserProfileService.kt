package agent.impl.openai.memory.ports

interface MemoryUserProfileService {
    fun buildProfileInstruction(userId: String): String
}
