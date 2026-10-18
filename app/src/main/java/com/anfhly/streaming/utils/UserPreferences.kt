package com.anfhly.streaming.utils

import android.content.Context
import com.anfhly.streaming.data.local.SettingsDataStore

/**
 * Manage user preferences and settings for sidebar and header configuration
 */
object UserPreferences {
    var currentProvider: Provider? = null
    var subtitleName: String? = null
    var serverAutoSubtitlesDisabled: Boolean = false
    
    data class Provider(
        val name: String,
        val baseUrl: String? = null
    )


    fun isSidebarOnly(context: Context): Boolean {
        return SettingsDataStore(context).isSidebarOnly()
    }

    fun isTabEnabled(context: Context, tabIndex: Int): Boolean {
        return SettingsDataStore(context).isTabEnabled(tabIndex)
    }

    fun setSidebarOnly(context: Context, value: Boolean) {
        SettingsDataStore(context).saveSidebarOnly(value)
    }

    fun setTabEnabled(context: Context, tabIndex: Int, enabled: Boolean) {
        SettingsDataStore(context).saveTabEnabled(tabIndex, enabled)
    }

    fun getSidebarOnly(context: Context): Boolean {
        return SettingsDataStore(context).isSidebarOnly()
    }

    fun getTabEnabled(context: Context, tabIndex: Int): Boolean {
        return SettingsDataStore(context).isTabEnabled(tabIndex)
    }

    fun saveSidebarOnly(context: Context, value: Boolean) {
        SettingsDataStore(context).saveSidebarOnly(value)
    }

    fun saveTabEnabled(context: Context, tabIndex: Int, value: Boolean) {
        SettingsDataStore(context).saveTabEnabled(tabIndex, value)
    }

    fun getPreferredPlayer(context: Context): String {
        return SettingsDataStore(context).getPreferredPlayer()
    }

    fun savePreferredPlayer(context: Context, value: String) {
        SettingsDataStore(context).savePreferredPlayer(value)
    }

    fun getUiStyle(context: Context): String {
        return SettingsDataStore(context).getUiStyle()
    }

    fun saveUiStyle(context: Context, value: String) {
        SettingsDataStore(context).saveUiStyle(value)
    }
}