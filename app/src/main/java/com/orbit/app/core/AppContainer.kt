package com.orbit.app.core

import android.content.Context
import com.orbit.app.chat.ChatService
import com.orbit.app.core.security.SecretStore
import com.orbit.app.data.db.OrbitDatabase
import com.orbit.app.data.prefs.SettingsRepository
import com.orbit.app.data.repo.AssistantRepository
import com.orbit.app.data.repo.ConversationRepository
import com.orbit.app.provider.ProviderEngine
import com.orbit.app.provider.ProviderRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val database: OrbitDatabase by lazy { OrbitDatabase.build(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val secrets: SecretStore by lazy { SecretStore(appContext) }
    val providers: ProviderRepository by lazy {
        ProviderRepository(database.providerDao(), database.modelDao(), secrets)
    }
    val conversations: ConversationRepository by lazy {
        ConversationRepository(database.conversationDao(), database.messageDao())
    }
    val assistants: AssistantRepository by lazy {
        AssistantRepository(database.assistantDao())
    }
    val providerEngine: ProviderEngine by lazy { ProviderEngine(httpClient, json) }
    val chat: ChatService by lazy {
        ChatService(applicationScope, conversations, providers, providerEngine)
    }
}
