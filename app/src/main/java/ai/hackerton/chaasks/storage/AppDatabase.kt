package ai.hackerton.chaasks.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
abstract class ConversationDao {
    @Insert
    abstract suspend fun insertSession(session: SessionEntity)

    @Insert
    abstract suspend fun insertTurns(turns: List<TurnEntity>)

    @Transaction
    open suspend fun insertSessionWithTurns(session: SessionEntity, turns: List<TurnEntity>) {
        insertSession(session)
        insertTurns(turns)
    }

    @Query("SELECT * FROM session ORDER BY startedAt DESC")
    abstract fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT topicId FROM session WHERE mode = 'TALK_SHOW' ORDER BY startedAt DESC LIMIT 1")
    abstract suspend fun lastTalkShowTopicId(): String?
}

/** SQLCipher로 암호화된 대화 DB. 암호 문구는 [DbPassphrase]가 Keystore로 보호한다. */
@Database(entities = [SessionEntity::class, TurnEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        const val NAME = "conversations.db"

        fun open(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(DbPassphrase.getOrCreate(context)))
                .build()
        }
    }
}
