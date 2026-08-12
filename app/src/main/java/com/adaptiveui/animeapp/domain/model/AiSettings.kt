package com.adaptiveui.animeapp.domain.model

import kotlinx.serialization.Serializable

/**
 * User-facing AI settings persisted in DataStore. The user picks a PROVIDER (Groq or Gemini),
 * then selects a MODEL from that provider's list (or enters a custom model ID). The user pastes
 * their own API key.
 */
@Serializable
data class AiSettings(
    val enabled: Boolean = false,
    val quickEditEnabled: Boolean = false,
    val provider: String = "Groq",
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val apiKey: String = "",
    val modelId: String = "openai/gpt-oss-20b",
    val temperature: Double = 0.3,
    val useCustomModel: Boolean = false,
    val customModelId: String = "",
    val githubToken: String = ""
) {
    val effectiveModelId: String get() = if (useCustomModel) customModelId.ifBlank { modelId } else modelId
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank()
}

@Serializable
data class FreeModelPreset(
    val label: String,
    val provider: String,
    val baseUrl: String,
    val modelId: String,
    val keySignupUrl: String,
    val contextWindow: Int,
    val notes: String
)
