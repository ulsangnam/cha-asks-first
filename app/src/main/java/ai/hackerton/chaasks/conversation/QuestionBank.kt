package ai.hackerton.chaasks.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QuestionBank(val topics: List<Topic>, val dailyLog: DailyLog) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): QuestionBank = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class Topic(
    val id: String,
    val title: String,
    val opening: String,
    val questions: List<Question>,
    val closing: String,
)

@Serializable
data class Question(
    val id: String,
    val text: String,
    val depth: Depth,
    val followUps: List<FollowUp> = emptyList(),
)

@Serializable
enum class Depth {
    @SerialName("light") LIGHT,
    @SerialName("deep") DEEP,
}

@Serializable
data class FollowUp(val keywords: List<String>, val text: String)

@Serializable
data class DailyLog(val arrival: String)
