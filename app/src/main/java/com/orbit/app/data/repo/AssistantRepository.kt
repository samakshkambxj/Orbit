package com.orbit.app.data.repo

import com.orbit.app.data.db.AssistantDao
import com.orbit.app.data.db.AssistantEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class AssistantInput(
    val id: String? = null,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    val providerId: String,
    val modelId: String,
)

class AssistantRepository(private val assistantDao: AssistantDao) {
    fun observeAll(): Flow<List<AssistantEntity>> = assistantDao.observeAll()

    suspend fun get(id: String): AssistantEntity? = assistantDao.get(id)

    suspend fun save(input: AssistantInput): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = input.name.trim()
            require(name.isNotEmpty()) { "Assistant name cannot be empty." }
            require(name.length <= 80) { "Assistant name is too long." }
            require(input.providerId.isNotBlank()) { "Choose a provider." }
            require(input.modelId.isNotBlank()) { "Choose a model." }
            val existing = input.id?.let { assistantDao.get(it) }
            val id = existing?.id ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            assistantDao.upsert(
                AssistantEntity(
                    id = id,
                    name = name,
                    emoji = input.emoji.trim().ifBlank { "✦" }.take(8),
                    systemPrompt = input.systemPrompt.trim().take(MAX_PROMPT_CHARS),
                    providerId = input.providerId,
                    modelId = input.modelId,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            id
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        assistantDao.delete(id)
    }

    private companion object {
        const val MAX_PROMPT_CHARS = 12_000
    }
}
