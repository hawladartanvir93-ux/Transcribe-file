package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AsrEngineMode
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vaultscribe_prefs")

data class UserSettings(
    val selectedLanguageId: String = "en-us",
    val customBcp47Tag: String = "",
    val selectedEngineMode: AsrEngineMode = AsrEngineMode.AUTO,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val useDynamicColor: Boolean = false,
    val includeTimestampsInTxt: Boolean = true,
    val maxCueChars: Int = 42,
    val autoLockOnBackground: Boolean = false,
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val SELECTED_LANGUAGE_ID = stringPreferencesKey("selected_language_id")
        val CUSTOM_BCP47 = stringPreferencesKey("custom_bcp47")
        val ENGINE_MODE = stringPreferencesKey("engine_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val TXT_TIMESTAMPS = booleanPreferencesKey("txt_timestamps")
        val MAX_CUE_CHARS = intPreferencesKey("max_cue_chars")
        val AUTO_LOCK_BG = booleanPreferencesKey("auto_lock_bg")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            selectedLanguageId = prefs[Keys.SELECTED_LANGUAGE_ID] ?: "en-us",
            customBcp47Tag = prefs[Keys.CUSTOM_BCP47] ?: "",
            selectedEngineMode = prefs[Keys.ENGINE_MODE]?.let {
                runCatching { AsrEngineMode.valueOf(it) }.getOrNull()
            } ?: AsrEngineMode.AUTO,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            includeTimestampsInTxt = prefs[Keys.TXT_TIMESTAMPS] ?: true,
            maxCueChars = prefs[Keys.MAX_CUE_CHARS] ?: 42,
            autoLockOnBackground = prefs[Keys.AUTO_LOCK_BG] ?: false,
        )
    }

    suspend fun setSelectedLanguage(languageId: String) {
        context.dataStore.edit { it[Keys.SELECTED_LANGUAGE_ID] = languageId }
    }

    suspend fun setCustomBcp47(tag: String) {
        context.dataStore.edit { it[Keys.CUSTOM_BCP47] = tag.trim() }
    }

    suspend fun setEngineMode(mode: AsrEngineMode) {
        context.dataStore.edit { it[Keys.ENGINE_MODE] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setIncludeTimestampsInTxt(include: Boolean) {
        context.dataStore.edit { it[Keys.TXT_TIMESTAMPS] = include }
    }

    suspend fun setMaxCueChars(maxChars: Int) {
        context.dataStore.edit { it[Keys.MAX_CUE_CHARS] = maxChars.coerceIn(24, 80) }
    }

    suspend fun setAutoLockOnBackground(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_LOCK_BG] = enabled }
    }
}
