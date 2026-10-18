package com.anfhly.streaming.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anfhly.streaming.data.model.UserSettings
import com.anfhly.streaming.data.local.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _focusedSectionIndex = MutableStateFlow(0)
    val focusedSectionIndex: StateFlow<Int> = _focusedSectionIndex.asStateFlow()

    private val _focusedItemIndex = MutableStateFlow(0)
    val focusedItemIndex: StateFlow<Int> = _focusedItemIndex.asStateFlow()

    // Settings sections structure
    val sections = listOf(
        SettingsSection("Audio", listOf("audioEnabled", "defaultAudioLanguage")),
        SettingsSection("Subtítulos", listOf("subtitlesEnabled", "defaultSubtitleLanguage")),
        SettingsSection("Video", listOf("autoQuality", "defaultVideoQuality")),
        SettingsSection("General", listOf("autoplay", "preferredLanguage")),
        SettingsSection("Privacidad", listOf("parentalControl", "notifications")),
        SettingsSection("Cuenta", listOf("logout", "deleteAccount"))
    )

    init {
        viewModelScope.launch {
            settingsDataStore.userSettings.collect { settings ->
                _settings.value = settings
            }
        }
    }

    fun updateSetting(key: String, value: Any) {
        viewModelScope.launch {
            val current = _settings.value
            val newSettings = when (key) {
                "audioEnabled" -> current.copy(audioEnabled = value as Boolean)
                "defaultAudioLanguage" -> current.copy(defaultAudioLanguage = value as String)
                "subtitlesEnabled" -> current.copy(subtitlesEnabled = value as Boolean)
                "defaultSubtitleLanguage" -> current.copy(defaultSubtitleLanguage = value as String)
                "autoQuality" -> current.copy(autoQuality = value as Boolean)
                "defaultVideoQuality" -> current.copy(defaultVideoQuality = value as String)
                "autoplay" -> current.copy(autoplay = value as Boolean)
                "preferredLanguage" -> current.copy(preferredLanguage = value as String)
                "parentalControl" -> current.copy(parentalControl = value as Boolean)
                "notifications" -> current.copy(notifications = value as Boolean)
                else -> current
            }
            
            settingsDataStore.saveSettings(newSettings)
            _isSaved.value = true
            
            // Reset saved indicator after 2 seconds
            kotlinx.coroutines.delay(2000)
            _isSaved.value = false
        }
    }

    fun navigateUp() {
        val totalItems = sections.sumOf { it.items.size }
        _focusedItemIndex.value = (_focusedItemIndex.value - 1).coerceAtLeast(0)
        updateFocusedSection()
    }

    fun navigateDown() {
        val totalItems = sections.sumOf { it.items.size }
        _focusedItemIndex.value = (_focusedItemIndex.value + 1).coerceAtMost(totalItems - 1)
        updateFocusedSection()
    }

    private fun updateFocusedSection() {
        var currentIndex = 0
        for ((sectionIndex, section) in sections.withIndex()) {
            for ((itemIndex, _) in section.items.withIndex()) {
                if (currentIndex == _focusedItemIndex.value) {
                    _focusedSectionIndex.value = sectionIndex
                    return
                }
                currentIndex++
            }
        }
    }

    fun getCurrentItem(): SettingsItem? {
        var currentIndex = 0
        for (section in sections) {
            for (item in section.items) {
                if (currentIndex == _focusedItemIndex.value) {
                    return SettingsItem(item, section.name)
                }
                currentIndex++
            }
        }
        return null
    }

    fun isItemFocused(sectionIndex: Int, itemIndex: Int): Boolean {
        var currentIndex = 0
        for (i in 0 until sectionIndex) {
            currentIndex += sections[i].items.size
        }
        currentIndex += itemIndex
        return currentIndex == _focusedItemIndex.value
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsDataStore.resetToDefaults()
        }
    }

    data class SettingsSection(val name: String, val items: List<String>)
    data class SettingsItem(val key: String, val sectionName: String)
}
