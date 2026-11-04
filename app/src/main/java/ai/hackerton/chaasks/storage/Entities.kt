package ai.hackerton.chaasks.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 대화 세션 1회. 시각은 epoch millis. */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: String,
    val mode: String,
    val topicId: String?,
    val startedAt: Long,
    val endedAt: Long,
    val destinationLabel: String,
    val summary: String,
)

/** 세션 안의 발화 하나. speaker는 HOST 또는 DRIVER. */
@Entity(
    tableName = "turn",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val speaker: String,
    val text: String,
    val createdAt: Long,
)
