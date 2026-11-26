package com.orbit.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orbit.app.data.model.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.orbitDataStore: DataStore<Preferences> by preferencesDataStore(name = "orbit_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultProviderId: String = "",
    val defaultModelId: String = "",
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.orbitDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                themeMode = prefs[THEME_MODE].toThemeMode(),
                dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
                defaultProviderId = prefs[DEFAULT_PROVIDER] ?: "",
                defaultModelId = prefs[DEFAULT_MODEL] ?: "",
            )
        }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[THEME_MODE] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[DYNAMIC_COLOR] = value }
    suspend fun setDefaultProvider(value: String) = edit { it[DEFAULT_PROVIDER] = value }
    suspend fun setDefaultModel(value: String) = edit { it[DEFAULT_MODEL] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.orbitDataStore.edit(block)
    }

    private fun String?.toThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(this.orEmpty()) }.getOrDefault(ThemeMode.SYSTEM)

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
    }
}
