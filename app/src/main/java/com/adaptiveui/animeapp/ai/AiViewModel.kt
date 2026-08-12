package com.adaptiveui.animeapp.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adaptiveui.animeapp.core.datastore.SettingsDataStore
import com.adaptiveui.animeapp.domain.model.AiSettings
import com.adaptiveui.animeapp.interpreter.ScreenSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State machine for the AI generation flow. The states are deliberately a flat sealed hierarchy
 * so the UI can `when` over them exhaustively without nested loading flags.
 *
 * Lifecycle of a typical preview flow:
 *   Idle → Generating → PreviewReady(spec) → (applyPreview → Idle) | (discardPreview → Idle)
 *                                                          ↘ Error(msg) → dismiss → Idle
 *
 * Lifecycle of a typical build flow:
 *   Idle → Generating → ComposeReady(source, screenName) → triggerBuild → BuildTriggered(runUrl) | Error(msg)
 */
sealed interface AiState {
    /** Nothing in flight. The AI bubble shows its idle prompt. */
    data object Idle : AiState

    /** A request is in flight (spec gen, compose gen, or build trigger). */
    data object Generating : AiState

    /** AI returned a parsed [ScreenSpec]. The UI shows a live preview + Apply / Discard buttons. */
    data class PreviewReady(val spec: ScreenSpec, val screenName: String) : AiState

    /** AI returned raw Kotlin source. The UI shows the code + "Build APK" button. */
    data class ComposeReady(val source: String, val screenName: String) : AiState

    /** Any failure (network, parse, GitHub). [message] is user-facing. */
    data class Error(val message: String) : AiState

    /** File pushed + workflow dispatched. [runUrl] opens the Actions run in a browser. */
    data class BuildTriggered(val runUrl: String) : AiState
}

/**
 * ViewModel backing the AI quick-edit bubble + the Settings "Test connection" button.
 *
 * Exposes:
 *  - [aiSettings]: the persisted settings (so the bubble can short-circuit if not configured).
 *  - [state]: the sealed [AiState] machine.
 *  - [testResult]: a separate slot for the Settings "Test connection" button (so testing doesn't
 *    clobber an in-flight generation state).
 *
 * All async work happens in [viewModelScope]; failures are caught and folded into [AiState.Error].
 */
@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiService: AiService,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    val aiSettings: StateFlow<AiSettings> = dataStore.aiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiSettings())

    private val _state = MutableStateFlow<AiState>(AiState.Idle)
    val state: StateFlow<AiState> = _state.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    /** The screen the most recent generation was for — used by [applyPreview] / [triggerBuild]. */
    private var pendingScreenName: String = "default"

    /**
     * Generate a ScreenSpec for live preview. The spec lands in [AiState.PreviewReady] on
     * success; the UI then calls [applyPreview] to persist it for the interpreter, or
     * [discardPreview] to throw it away.
     */
    fun generateSpec(instruction: String, screenName: String, availableData: String) {
        viewModelScope.launch {
            _state.value = AiState.Generating
            _testResult.value = null
            pendingScreenName = screenName
            val settings = aiSettings.value
            if (!settings.isConfigured) {
                _state.value = AiState.Error("AI is not configured — add an API key and base URL in Settings.")
                return@launch
            }
            aiService.generateScreenSpec(settings, instruction, screenName, availableData)
                .onSuccess { _state.value = AiState.PreviewReady(it, screenName) }
                .onFailure { _state.value = AiState.Error(it.message ?: "Unknown AI error") }
        }
    }

    /**
     * Generate a Compose `.kt` file for permanent build. The source lands in
     * [AiState.ComposeReady] on success; the UI then shows the code and a "Build APK" button
     * that calls [triggerBuild].
     */
    fun generateCompose(instruction: String, screenName: String, availableData: String) {
        viewModelScope.launch {
            _state.value = AiState.Generating
            _testResult.value = null
            pendingScreenName = screenName
            val settings = aiSettings.value
            if (!settings.isConfigured) {
                _state.value = AiState.Error("AI is not configured — add an API key and base URL in Settings.")
                return@launch
            }
            aiService.generateComposeFile(settings, instruction, screenName, availableData)
                .onSuccess { _state.value = AiState.ComposeReady(it, screenName) }
                .onFailure { _state.value = AiState.Error(it.message ?: "Unknown AI error") }
        }
    }

    /**
     * Persist a previewed [ScreenSpec] to DataStore under [pendingScreenName] so the interpreter
     * picks it up on the next composition of that screen. Returns to [AiState.Idle].
     */
    fun applyPreview(spec: ScreenSpec) {
        viewModelScope.launch {
            dataStore.saveSpec(pendingScreenName, spec)
            _state.value = AiState.Idle
        }
    }

    /** Throw away the current preview without persisting. Returns to [AiState.Idle]. */
    fun discardPreview() {
        _state.value = AiState.Idle
    }

    /**
     * Push a generated Compose file to GitHub and trigger the `build-generated.yml` workflow.
     * On success, [AiState.BuildTriggered] holds the Actions run URL (a ntfy notification will
     * fire when the APK finishes building — the workflow handles that).
     */
    fun triggerBuild(composeSource: String, screenName: String) {
        viewModelScope.launch {
            _state.value = AiState.Generating
            val token = aiSettings.value.githubToken
            if (token.isBlank()) {
                _state.value = AiState.Error("GitHub token not set. Add it in Settings → AI → GitHub Token.")
                return@launch
            }
            aiService.pushAndTriggerBuild(composeSource, screenName, token)
                .onSuccess { _state.value = AiState.BuildTriggered(it) }
                .onFailure { _state.value = AiState.Error(it.message ?: "Build trigger failed") }
        }
    }

    /**
     * Send a minimal ping request to the configured AI provider. Updates [testResult] (does
     * NOT touch [state]) so the Settings screen can show the result without disrupting any
     * in-flight generation.
     */
    fun testConnection() {
        viewModelScope.launch {
            val settings = aiSettings.value
            _testResult.value = aiService.testConnection(settings)
        }
    }

    /** Clear the [testResult] slot (e.g. when the user edits settings after testing). */
    fun clearTestResult() {
        _testResult.value = null
    }

    /** Dismiss the current state — returns to [AiState.Idle] and clears [testResult]. */
    fun dismiss() {
        _state.value = AiState.Idle
        _testResult.value = null
    }
}
