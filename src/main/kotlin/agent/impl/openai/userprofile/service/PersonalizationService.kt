package agent.impl.openai.userprofile.service

import agent.impl.openai.userprofile.model.UserProfile

interface PersonalizationService {
    fun buildProfileInstruction(profile: UserProfile): String
}

class PersonalizationServiceImpl : PersonalizationService {

    override fun buildProfileInstruction(profile: UserProfile): String {
        val parts = mutableListOf<String>()

        parts += "Профиль пользователя:"
        parts += "- язык: ${profile.style.language}"
        parts += "- тон: ${profile.style.tone}"
        parts += "- подробность: ${profile.style.verbosity}"

        parts += "Формат ответа:"
        parts += "- сначала код: ${yesNo(profile.format.preferCodeFirst)}"
        parts += "- нужны примеры: ${yesNo(profile.format.preferExamples)}"
        parts += "- пошаговое объяснение: ${yesNo(profile.format.preferStepByStep)}"
        parts += "- короткие списки: ${yesNo(profile.format.preferShortLists)}"

        parts += "Ограничения:"
        parts += "- избегать branching: ${yesNo(profile.constraints.avoidBranching)}"
        parts += "- избегать OkHttp: ${yesNo(profile.constraints.avoidOkHttp)}"
        parts += "- использовать Ktor по умолчанию: ${yesNo(profile.constraints.useKtorByDefault)}"
        parts += "- сохранять существующие интерфейсы: ${yesNo(profile.constraints.keepExistingInterfaces)}"

        if (profile.preferences.techStack.isNotEmpty()) {
            parts += "Предпочитаемый стек:"
            profile.preferences.techStack.forEach { (k, v) ->
                parts += "- $k: $v"
            }
        }

        if (profile.preferences.answerPatterns.isNotEmpty()) {
            parts += "Паттерны ответа:"
            profile.preferences.answerPatterns.forEach {
                parts += "- $it"
            }
        }

        return parts.joinToString("\n")
    }

    private fun yesNo(value: Boolean): String = if (value) "да" else "нет"
}