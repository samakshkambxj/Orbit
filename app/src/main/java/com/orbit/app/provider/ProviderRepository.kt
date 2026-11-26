package com.orbit.app.provider

import android.net.Uri
import com.orbit.app.core.security.SecretStore
import com.orbit.app.data.db.ModelDao
import com.orbit.app.data.db.ModelEntity
import com.orbit.app.data.db.ProviderDao
import com.orbit.app.data.db.ProviderEntity
import com.orbit.app.data.model.ApiFormat
import com.orbit.app.data.model.ProviderConfig
import com.orbit.app.data.model.ProviderInput
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProviderRepository(
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val secrets: SecretStore,
) {
    fun observeAll(): Flow<List<ProviderEntity>> = providerDao.observeAll()
    fun observeModels(providerId: String): Flow<List<ModelEntity>> = modelDao.observeForProvider(providerId)
    fun observeAllModels(): Flow<List<ModelEntity>> = modelDao.observeAll()

    suspend fun get(id: String): ProviderEntity? = providerDao.get(id)
    suspend fun modelsFor(providerId: String): List<ModelEntity> = modelDao.forProvider(providerId)

    suspend fun save(input: ProviderInput): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = input.name.trim()
            require(name.isNotEmpty()) { "Provider name cannot be empty." }
            require(name.length <= 80) { "Provider name is too long." }
            val baseUrl = normalizeBaseUrl(input.baseUrl)
            val modelId = input.modelId.trim()
            if (modelId.isNotEmpty()) {
                require(modelId.none { it.isWhitespace() || it.isISOControl() }) {
                    "Model IDs cannot contain spaces or control characters."
                }
            }
            val existing = input.id?.let { providerDao.get(it) }
            val id = existing?.id ?: UUID.randomUUID().toString()
            val keyId = keyId(id)
            if (!input.apiKey.isNullOrBlank()) {
                secrets.put(keyId, input.apiKey.trim())
            }
            val now = System.currentTimeMillis()
            providerDao.upsert(
                ProviderEntity(
                    id = id,
                    name = name,
                    baseUrl = baseUrl,
                    apiFormat = input.apiFormat.name,
                    hasKey = secrets.has(keyId),
                    enabled = true,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            if (modelId.isNotEmpty()) {
                modelDao.upsert(
                    ModelEntity(
                        id = "$id:$modelId",
                        providerId = id,
                        modelId = modelId,
                        displayName = input.modelDisplayName.trim().ifBlank { modelId },
                        enabled = true,
                    ),
                )
            }
            id
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        secrets.remove(keyId(id))
        modelDao.deleteForProvider(id)
        providerDao.delete(id)
    }

    suspend fun deleteModel(id: String) = withContext(Dispatchers.IO) {
        modelDao.delete(id)
    }

    suspend fun importModels(providerId: String, ids: List<String>): Int =
        withContext(Dispatchers.IO) {
            val existing = modelDao.forProvider(providerId).map { it.modelId }.toSet()
            val added = ids.filter { it !in existing }
            added.forEach { modelId ->
                modelDao.upsert(
                    ModelEntity(
                        id = "$providerId:$modelId",
                        providerId = providerId,
                        modelId = modelId,
                        displayName = modelId,
                        enabled = true,
                    ),
                )
            }
            added.size
        }

    suspend fun configFor(id: String): ProviderConfig? {
        val provider = providerDao.get(id) ?: return null
        val format = runCatching { ApiFormat.valueOf(provider.apiFormat) }.getOrDefault(ApiFormat.OPENAI)
        return ProviderConfig(
            id = provider.id,
            name = provider.name,
            baseUrl = provider.baseUrl,
            apiFormat = format,
            apiKey = secrets.get(keyId(provider.id)),
            timeoutMs = 120_000,
        )
    }

    private fun normalizeBaseUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        require(value.isNotEmpty()) { "Base URL cannot be empty." }
        val uri = Uri.parse(value)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") { "Base URL must start with https:// or http://." }
        require(!uri.host.isNullOrBlank()) { "Base URL must include a host." }
        if (scheme == "http") {
            require(uri.host == "localhost" || uri.host == "10.0.2.2" || uri.host == "127.0.0.1") {
                "Plain HTTP is only allowed for localhost, 10.0.2.2 or 127.0.0.1. Use HTTPS for remote providers."
            }
        }
        return value
    }

    private fun keyId(providerId: String): String = "provider-key-$providerId"
}
