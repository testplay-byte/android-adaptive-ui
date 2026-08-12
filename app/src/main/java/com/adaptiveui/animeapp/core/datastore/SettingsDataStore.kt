package com.adaptiveui.animeapp.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adaptiveui.animeapp.domain.model.AiSettings
import com.adaptiveui.animeapp.interpreter.ScreenSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(name = "adaptive_ui_prefs")

/**
 * Single DataStore holding all user preferences: the active AI-generated [ScreenSpec]s (one
 * per screen, for the live interpreter), [AiSettings], and quick toggles.
 *
 * Each screen's spec is stored as a JSON envelope `Map<String, ScreenSpec>` in one key — when
 * the user applies an AI preview via [com.adaptiveui.animeapp.ai.AiViewModel.applyPreview], it
 * lands here and the [com.adaptiveui.animeapp.interpreter.UiSpecInterpreter] picks it up on
 * the next composition. If no spec is saved for a screen, the interpreter falls back to the
 * built-in default layout.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val KEY_SCREEN_SPECS = stringPreferencesKey("screen_specs_json")
    private val KEY_AI_SETTINGS = stringPreferencesKey("ai_settings_json")
    private val KEY_QUICK_EDIT = booleanPreferencesKey("quick_edit_enabled")
    private val KEY_FIRST_RUN = booleanPreferencesKey("first_run_done")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

    /**
     * All saved screen specs keyed by screen name. Empty map if the user has never applied one.
     */
    val screenSpecs: Flow<Map<String, ScreenSpec>> = context.uiDataStore.data.map { prefs ->
        prefs[KEY_SCREEN_SPECS]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, ScreenSpec>>(raw) }.getOrNull()
        } ?: emptyMap()
    }

    /** Convenience flow: the spec for a single screen, or null if none saved. */
    fun specForScreen(screenName: String): Flow<ScreenSpec?> =
        context.uiDataStore.data.map { prefs ->
            prefs[KEY_SCREEN_SPECS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ScreenSpec>>(raw) }.getOrNull()
            }?.get(screenName)
        }

    val aiSettings: Flow<AiSettings> = context.uiDataStore.data.map { prefs ->
        prefs[KEY_AI_SETTINGS]?.let { runCatching { json.decodeFromString<AiSettings>(it) }.getOrNull() } ?: AiSettings()
    }

    val quickEditEnabled: Flow<Boolean> = context.uiDataStore.data.map { it[KEY_QUICK_EDIT] ?: false }

    /** "AUTO" | "LIGHT" | "DARK" */
    val themeMode: Flow<String> = context.uiDataStore.data.map { it[KEY_THEME_MODE] ?: "AUTO" }

    val isFirstRun: Flow<Boolean> = context.uiDataStore.data.map { it[KEY_FIRST_RUN] ?: true }

    /**
     * Apply (or replace) the spec for a single screen. Other screens' specs are preserved.
     * Called by [com.adaptiveui.animeapp.ai.AiViewModel.applyPreview].
     */
    suspend fun saveSpec(screenName: String, spec: ScreenSpec) {
        context.uiDataStore.edit { prefs ->
            val current = prefs[KEY_SCREEN_SPECS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ScreenSpec>>(raw) }.getOrNull()
            } ?: emptyMap()
            val updated = current + (screenName to spec)
            prefs[KEY_SCREEN_SPECS] = json.encodeToString(updated)
        }
    }

    /** Convenience: save a spec under the default screen-name key "default". */
    suspend fun saveSpec(spec: ScreenSpec) = saveSpec("default", spec)

    /** Remove the spec for a single screen (revert to the built-in default). */
    suspend fun clearSpec(screenName: String) {
        context.uiDataStore.edit { prefs ->
            val current = prefs[KEY_SCREEN_SPECS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ScreenSpec>>(raw) }.getOrNull()
            } ?: emptyMap()
            val updated = current - screenName
            prefs[KEY_SCREEN_SPECS] = if (updated.isEmpty()) null else json.encodeToString(updated)
        }
    }

    /** Wipe ALL saved specs (used by the Settings "Reset to defaults" action). */
    suspend fun clearAllSpecs() {
        context.uiDataStore.edit { it.remove(KEY_SCREEN_SPECS) }
    }

    suspend fun saveAiSettings(settings: AiSettings) {
        context.uiDataStore.edit { it[KEY_AI_SETTINGS] = json.encodeToString(AiSettings.serializer(), settings) }
    }

    suspend fun setQuickEditEnabled(enabled: Boolean) {
        context.uiDataStore.edit { it[KEY_QUICK_EDIT] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.uiDataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun markFirstRunDone() {
        context.uiDataStore.edit { it[KEY_FIRST_RUN] = false }
    }
}
