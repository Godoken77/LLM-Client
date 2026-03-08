package agent.impl.openai.userprofile

import agent.impl.openai.userprofile.model.UserProfile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

interface UserProfileRepository {
    fun load(userId: String): UserProfile
    fun save(profile: UserProfile)
    fun delete(userId: String)
}

class FileUserProfileRepository(
    private val dataDir: File = File("./data/profiles"),
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : UserProfileRepository {

    init {
        if (!dataDir.exists()) dataDir.mkdirs()
    }

    override fun load(userId: String): UserProfile {
        val file = fileFor(userId)
        if (!file.exists()) return UserProfile(userId = userId)

        return try {
            file.reader(Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, UserProfile::class.java) ?: UserProfile(userId = userId)
            }
        } catch (_: Exception) {
            UserProfile(userId = userId)
        }
    }

    override fun save(profile: UserProfile) {
        val file = fileFor(profile.userId)
        if (!file.parentFile.exists()) file.parentFile.mkdirs()

        val tmp = File(file.absolutePath + ".tmp")
        tmp.writer(Charsets.UTF_8).use { writer ->
            gson.toJson(profile, writer)
        }

        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    override fun delete(userId: String) {
        val file = fileFor(userId)
        if (file.exists()) file.delete()
    }

    private fun fileFor(userId: String): File {
        val safe = userId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dataDir, "$safe.json")
    }
}