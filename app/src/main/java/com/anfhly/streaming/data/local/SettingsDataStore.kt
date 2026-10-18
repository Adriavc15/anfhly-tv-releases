package com.anfhly.streaming.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anfhly.streaming.data.model.UserProfile
import com.anfhly.streaming.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anfhly_settings")

class SettingsDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val DEFAULT_AUDIO_LANGUAGE = stringPreferencesKey("default_audio_language")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val DEFAULT_SUBTITLE_LANGUAGE = stringPreferencesKey("default_subtitle_language")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
        val AUTO_QUALITY = booleanPreferencesKey("auto_quality")
        val AUTOPLAY = booleanPreferencesKey("autoplay")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val PARENTAL_CONTROL = booleanPreferencesKey("parental_control")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val SELECTED_PROFILE = stringPreferencesKey("selected_profile")
        val REMEMBERED_EMAIL = stringPreferencesKey("remembered_email")
        val SIDEBAR_ONLY = booleanPreferencesKey("sidebar_only")
        val TAB_ENABLED_1 = booleanPreferencesKey("tab_enabled_1")
        val TAB_ENABLED_2 = booleanPreferencesKey("tab_enabled_2")
        val TAB_ENABLED_3 = booleanPreferencesKey("tab_enabled_3")
        val TAB_ENABLED_4 = booleanPreferencesKey("tab_enabled_4")
        val TAB_ENABLED_5 = booleanPreferencesKey("tab_enabled_5")
        val TAB_ENABLED_6 = booleanPreferencesKey("tab_enabled_6")
        val PREFERRED_PLAYER = stringPreferencesKey("preferred_player")
        val UI_STYLE = stringPreferencesKey("ui_style")
    }

    /**
     * Get user settings
     */
    val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            defaultAudioLanguage = preferences[DEFAULT_AUDIO_LANGUAGE] ?: "es",
            audioEnabled = preferences[AUDIO_ENABLED] ?: true,
            defaultSubtitleLanguage = preferences[DEFAULT_SUBTITLE_LANGUAGE] ?: "es",
            subtitlesEnabled = preferences[SUBTITLES_ENABLED] ?: true,
            defaultVideoQuality = preferences[DEFAULT_VIDEO_QUALITY] ?: "1080p",
            autoQuality = preferences[AUTO_QUALITY] ?: false,
            autoplay = preferences[AUTOPLAY] ?: true,
            notifications = preferences[NOTIFICATIONS] ?: true,
            parentalControl = preferences[PARENTAL_CONTROL] ?: false,
            preferredLanguage = preferences[PREFERRED_LANGUAGE] ?: "es"
        )
    }

    /**
     * Get sidebar only setting
     */
    fun isSidebarOnly(): Boolean {
        val preferences = runBlocking { context.dataStore.data.firstOrNull() } ?: return false
        return preferences[SIDEBAR_ONLY] ?: false
    }

    /**
     * Get tab enabled setting
     */
    fun isTabEnabled(tabIndex: Int): Boolean {
        val key = when (tabIndex) {
            1 -> TAB_ENABLED_1
            2 -> TAB_ENABLED_2
            3 -> TAB_ENABLED_3
            4 -> TAB_ENABLED_4
            5 -> TAB_ENABLED_5
            else -> TAB_ENABLED_6
        }
        val preferences = runBlocking { context.dataStore.data.firstOrNull() } ?: return true
        return preferences[key] ?: true
    }

    /**
     * Save sidebar only setting (suspend)
     */
    suspend fun setSidebarOnly(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SIDEBAR_ONLY] = value
        }
    }

    /**
     * Save sidebar only setting (non-suspend, for non-coroutine contexts)
     */
    fun saveSidebarOnly(value: Boolean) {
        runBlocking { setSidebarOnly(value) }
    }

    /**
     * Save tab enabled setting (suspend)
     */
    suspend fun setTabEnabled(tabIndex: Int, value: Boolean) {
        val key = when (tabIndex) {
            1 -> TAB_ENABLED_1
            2 -> TAB_ENABLED_2
            3 -> TAB_ENABLED_3
            4 -> TAB_ENABLED_4
            5 -> TAB_ENABLED_5
            else -> TAB_ENABLED_6
        }
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    /**
     * Save tab enabled setting (non-suspend, for non-coroutine contexts)
     */
    fun saveTabEnabled(tabIndex: Int, value: Boolean) {
        runBlocking { setTabEnabled(tabIndex, value) }
    }

    /**
     * Get preferred player
     */
    fun getPreferredPlayer(): String {
        val preferences = runBlocking { context.dataStore.data.firstOrNull() } ?: return "exoplayer"
        return preferences[PREFERRED_PLAYER] ?: "exoplayer"
    }

    /**
     * Save preferred player
     */
    suspend fun setPreferredPlayer(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PREFERRED_PLAYER] = value
        }
    }

    fun savePreferredPlayer(value: String) {
        runBlocking { setPreferredPlayer(value) }
    }

    /**
     * Get UI style
     */
    fun getUiStyle(): String {
        val preferences = runBlocking { context.dataStore.data.firstOrNull() } ?: return "apple"
        return preferences[UI_STYLE] ?: "apple"
    }

    /**
     * Save UI style
     */
    suspend fun setUiStyle(value: String) {
        context.dataStore.edit { preferences ->
            preferences[UI_STYLE] = value
        }
    }

    fun saveUiStyle(value: String) {
        runBlocking { setUiStyle(value) }
    }

    /**
     * Selected profile
     */
    val selectedProfile: Flow<UserProfile?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_PROFILE]?.let { jsonString ->
            try {
                json.decodeFromString<UserProfile>(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Save selected profile
     */
    suspend fun saveSelectedProfile(profile: UserProfile?) {
        context.dataStore.edit { preferences ->
            if (profile != null) {
                preferences[SELECTED_PROFILE] = json.encodeToString(profile)
            } else {
                preferences.remove(SELECTED_PROFILE)
            }
        }
    }

    /**
     * Remembered email
     */
    val rememberedEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REMEMBERED_EMAIL]
    }

    /**
     * Save remembered email
     */
    suspend fun saveRememberedEmail(email: String?) {
        context.dataStore.edit { preferences ->
            if (email != null) {
                preferences[REMEMBERED_EMAIL] = email
            } else {
                preferences.remove(REMEMBERED_EMAIL)
            }
        }
    }

    /**
     * Save full settings
     */
    suspend fun saveSettings(settings: UserSettings) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_AUDIO_LANGUAGE] = settings.defaultAudioLanguage
            preferences[AUDIO_ENABLED] = settings.audioEnabled
            preferences[DEFAULT_SUBTITLE_LANGUAGE] = settings.defaultSubtitleLanguage
            preferences[SUBTITLES_ENABLED] = settings.subtitlesEnabled
            preferences[DEFAULT_VIDEO_QUALITY] = settings.defaultVideoQuality
            preferences[AUTO_QUALITY] = settings.autoQuality
            preferences[AUTOPLAY] = settings.autoplay
            preferences[NOTIFICATIONS] = settings.notifications
            preferences[PARENTAL_CONTROL] = settings.parentalControl
            preferences[PREFERRED_LANGUAGE] = settings.preferredLanguage
        }
    }

    /**
     * Reset settings to defaults
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}