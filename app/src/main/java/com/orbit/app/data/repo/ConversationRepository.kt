package com.orbit.app.data.repo

import com.orbit.app.data.db.ConversationDao
import com.orbit.app.data.db.ConversationEntity
import com.orbit.app.data.db.MessageDao
import com.orbit.app.data.db.MessageEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {
    fun observeAll(): Flow<List<ConversationEntity>> = conversationDao.observeAll()
    fun observe(id: String): Flow<ConversationEntity?> = conversationDao.observe(id)
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeForConversation(conversationId)

    suspend fun get(id: String): ConversationEntity? = conversationDao.get(id)
    suspend fun messages(conversationId: String, limit: Int): List<MessageEntity> =
        messageDao.recent(conversationId, limit).reversed()

    suspend fun lastMessage(conversationId: String): MessageEntity? =
        messageDao.last(conversationId)

    suspend fun deleteMessage(id: String) = messageDao.delete(id)

    suspend fun updateMessageContent(id: String, content: String) =
        messageDao.updateContent(id, content)

    suspend fun create(
        providerId: String,
        modelId: String,
        title: String = "New chat",
        systemPrompt: String = "",
    ): ConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            providerId = providerId,
            modelId = modelId,
            systemPrompt = systemPrompt.trim(),
            createdAt = now,
            updatedAt = now,
        )
        conversationDao.upsert(conversation)
        return conversation
    }

    suspend fun rename(id: String, title: String) {
        val clean = title.trim().take(100).ifBlank { "New chat" }
        conversationDao.rename(id, clean, System.currentTimeMillis())
    }

    suspend fun touch(id: String, preview: String) {
        conversationDao.touch(id, preview.take(160), System.currentTimeMillis())
    }

    suspend fun setModel(id: String, providerId: String, modelId: String) {
        conversationDao.updateModel(id, providerId, modelId, System.currentTimeMillis())
    }

    suspend fun setSystemPrompt(id: String, prompt: String) {
        conversationDao.updateSystemPrompt(id, prompt.trim(), System.currentTimeMillis())
    }

    suspend fun setAgentEnabled(id: String, enabled: Boolean) {
        conversationDao.updateAgentEnabled(id, enabled, System.currentTimeMillis())
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        conversationDao.updatePinned(id, pinned)
    }

    suspend fun delete(id: String) {
        messageDao.deleteForConversation(id)
        conversationDao.delete(id)
    }

    suspend fun appendMessage(
        conversationId: String,
        role: String,
        content: String,
        attachments: String = "",
        toolCalls: String = "",
        toolCallId: String = "",
        toolName: String = "",
        toolError: Boolean = false,
        reasoning: String = "",
        status: String = "COMPLETE",
        error: String? = null,
    ): MessageEntity {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            attachments = attachments,
            toolCalls = toolCalls,
            toolCallId = toolCallId,
            toolName = toolName,
            toolError = toolError,
            reasoning = reasoning,
            status = status,
            error = error,
            createdAt = System.currentTimeMillis(),
        )
        messageDao.upsert(message)
        return message
    }

    suspend fun upsertMessage(message: MessageEntity) = messageDao.upsert(message)

    suspend fun markStreamingInterrupted() {
        messageDao.markStreamingInterrupted("Generation interrupted by app restart.")
    }
}
