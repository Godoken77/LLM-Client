package agent.impl.openai.userprofile.model

data class UserProfile(
    val userId: String,
    val style: UserStyle = UserStyle(),
    val format: UserFormat = UserFormat(),
    val constraints: UserConstraints = UserConstraints(),
    val preferences: UserPreferences = UserPreferences()
)

data class UserStyle(
    val tone: String = "neutral",            // neutral, concise, formal, friendly
    val verbosity: String = "medium",        // short, medium, detailed
    val language: String = "ru"
)

data class UserFormat(
    val preferCodeFirst: Boolean = false,
    val preferExamples: Boolean = true,
    val preferStepByStep: Boolean = true,
    val preferShortLists: Boolean = true
)

data class UserConstraints(
    val avoidBranching: Boolean = false,
    val avoidOkHttp: Boolean = false,
    val useKtorByDefault: Boolean = false,
    val keepExistingInterfaces: Boolean = false
)

data class UserPreferences(
    val techStack: MutableMap<String, String> = mutableMapOf(),
    val answerPatterns: MutableList<String> = mutableListOf()
)