package ai.hackerton.chaasks.storage

import ai.hackerton.chaasks.conversation.Mode
import ai.hackerton.chaasks.conversation.Turn
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

data class TimedTurn(val turn: Turn, val atMillis: Long)

data class SessionRecord(
    val mode: Mode,
    val topicId: String?,
    val startedAt: Long,
    val endedAt: Long,
    val destinationLabel: String,
    val summary: String,
    val turns: List<TimedTurn>,
)

/** 대화 기록 저장소. DB는 처음 쓸 때 열고, 동의 철회 시 [wipe]로 파일과 키를 지운다. */
class ConversationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var database: AppDatabase? = null

    private fun dao(): ConversationDao = synchronized(lock) {
        (database ?: AppDatabase.open(appContext).also { database = it }).conversationDao()
    }

    fun sessions(): Flow<List<SessionEntity>> = dao().observeSessions()

    suspend fun lastTalkShowTopicId(): String? = withContext(Dispatchers.IO) { dao().lastTalkShowTopicId() }

    suspend fun save(record: SessionRecord) = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val session = SessionEntity(
            id = id,
            mode = record.mode.name,
            topicId = record.topicId,
            startedAt = record.startedAt,
            endedAt = record.endedAt,
            destinationLabel = record.destinationLabel,
            summary = record.summary,
        )
        val turns = record.turns.map {
            TurnEntity(sessionId = id, speaker = it.turn.speaker.name, text = it.turn.text, createdAt = it.atMillis)
        }
        dao().insertSessionWithTurns(session, turns)
    }

    suspend fun wipe() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            database?.close()
            database = null
        }
        appContext.deleteDatabase(AppDatabase.NAME)
        DbPassphrase.delete(appContext)
    }
}
