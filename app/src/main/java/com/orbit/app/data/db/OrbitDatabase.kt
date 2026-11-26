package com.orbit.app.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val apiFormat: String,
    val hasKey: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "models", indices = [Index("providerId")])
data class ModelEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val enabled: Boolean = true,
)

@Entity(tableName = "conversations", indices = [Index("updatedAt")])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val lastPreview: String = "",
    @ColumnInfo(defaultValue = "''")
    val systemPrompt: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "assistants", indices = [Index("updatedAt")])
data class AssistantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId"), Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String = "",
    val status: String = "COMPLETE",
    val error: String? = null,
    val createdAt: Long,
)

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY displayName ASC")
    fun observeForProvider(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY displayName ASC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY displayName ASC")
    suspend fun forProvider(providerId: String): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelEntity)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long)

    @Query("UPDATE conversations SET lastPreview = :preview, updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, preview: String, now: Long)

    @Query("UPDATE conversations SET providerId = :providerId, modelId = :modelId, updatedAt = :now WHERE id = :id")
    suspend fun updateModel(id: String, providerId: String, modelId: String, now: Long)

    @Query("UPDATE conversations SET systemPrompt = :prompt, updatedAt = :now WHERE id = :id")
    suspend fun updateSystemPrompt(id: String, prompt: String, now: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AssistantDao {
    @Query("SELECT * FROM assistants ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AssistantEntity>>

    @Query("SELECT * FROM assistants WHERE id = :id")
    suspend fun get(id: String): AssistantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assistant: AssistantEntity)

    @Query("DELETE FROM assistants WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(conversationId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT 1")
    suspend fun last(conversationId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    @Query("UPDATE messages SET status = 'CANCELLED', error = :reason WHERE status = 'STREAMING'")
    suspend fun markStreamingInterrupted(reason: String)
}

@Database(
    entities = [
        ProviderEntity::class,
        ModelEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AssistantEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun assistantDao(): AssistantDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN systemPrompt TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assistants (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "emoji TEXT NOT NULL, " +
                        "systemPrompt TEXT NOT NULL, " +
                        "providerId TEXT NOT NULL, " +
                        "modelId TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_assistants_updatedAt " +
                        "ON assistants (updatedAt)",
                )
            }
        }

        fun build(context: Context): OrbitDatabase =
            Room.databaseBuilder(context, OrbitDatabase::class.java, "orbit.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
