package com.adaptiveui.animeapp.ai

import com.adaptiveui.animeapp.domain.model.FreeModelPreset

/**
 * Catalog of free / generous-tier OpenAI-compatible models for the AI Quick-Edit feature.
 *
 * The user picks a preset in Settings; the corresponding `baseUrl` + `modelId` get written to
 * [com.adaptiveui.animeapp.domain.model.AiSettings]. The user still needs to supply their own
 * API key (signed up via [FreeModelPreset.keySignupUrl]).
 *
 * Both providers expose OpenAI-compatible `/chat/completions` endpoints so the same
 * [com.adaptiveui.animeapp.core.network.openai.OpenAiApi] works for both — only the base URL
 * and model id change.
 */
object FreeModelPresets {

    const val PROVIDER_GROQ = "Groq"
    const val PROVIDER_GEMINI = "Gemini"

    private val GROQ_BASE = "https://api.groq.com/openai/v1"
    private val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai"

    val all: List<FreeModelPreset> = listOf(
        // ── Groq ──────────────────────────────────────────────────────────────
        FreeModelPreset(
            label = "gpt-oss-20b",
            provider = PROVIDER_GROQ,
            baseUrl = GROQ_BASE,
            modelId = "openai/gpt-oss-20b",
            keySignupUrl = "https://console.groq.com/keys",
            contextWindow = 128_000,
            notes = "OpenAI gpt-oss 20B — strong reasoning, fast on Groq LPU."
        ),
        FreeModelPreset(
            label = "gpt-oss-120b",
            provider = PROVIDER_GROQ,
            baseUrl = GROQ_BASE,
            modelId = "openai/gpt-oss-120b",
            keySignupUrl = "https://console.groq.com/keys",
            contextWindow = 128_000,
            notes = "OpenAI gpt-oss 120B — best Groq quality, may be slower."
        ),
        FreeModelPreset(
            label = "llama-3.3-70b-versatile",
            provider = PROVIDER_GROQ,
            baseUrl = GROQ_BASE,
            modelId = "llama-3.3-70b-versatile",
            keySignupUrl = "https://console.groq.com/keys",
            contextWindow = 128_000,
            notes = "Meta Llama 3.3 70B — reliable general purpose model."
        ),
        FreeModelPreset(
            label = "llama-3.1-8b-instant",
            provider = PROVIDER_GROQ,
            baseUrl = GROQ_BASE,
            modelId = "llama-3.1-8b-instant",
            keySignupUrl = "https://console.groq.com/keys",
            contextWindow = 128_000,
            notes = "Smaller Llama — fastest, lowest-quality option."
        ),

        // ── Gemini (OpenAI-compat endpoint) ──────────────────────────────────
        FreeModelPreset(
            label = "gemini-2.5-flash",
            provider = PROVIDER_GEMINI,
            baseUrl = GEMINI_BASE,
            modelId = "gemini-2.5-flash",
            keySignupUrl = "https://aistudio.google.com/apikey",
            contextWindow = 1_000_000,
            notes = "Google Gemini 2.5 Flash — fast, cheap, very long context."
        ),
        FreeModelPreset(
            label = "gemini-2.5-flash-lite",
            provider = PROVIDER_GEMINI,
            baseUrl = GEMINI_BASE,
            modelId = "gemini-2.5-flash-lite",
            keySignupUrl = "https://aistudio.google.com/apikey",
            contextWindow = 1_000_000,
            notes = "Smaller Gemini — most economical."
        ),
        FreeModelPreset(
            label = "gemini-2.5-pro",
            provider = PROVIDER_GEMINI,
            baseUrl = GEMINI_BASE,
            modelId = "gemini-2.5-pro",
            keySignupUrl = "https://aistudio.google.com/apikey",
            contextWindow = 1_000_000,
            notes = "Gemini 2.5 Pro — best Google quality."
        )
    )

    /** All presets for a given provider name (case-insensitive). */
    fun forProvider(provider: String): List<FreeModelPreset> =
        all.filter { it.provider.equals(provider, ignoreCase = true) }

    /** First preset matching a provider — used as a sensible default when the user switches. */
    fun defaultFor(provider: String): FreeModelPreset =
        forProvider(provider).firstOrNull() ?: all.first()

    /** Lookup by provider + model id (used to recover the preset from saved settings). */
    fun find(provider: String, modelId: String): FreeModelPreset? =
        all.firstOrNull { it.provider.equals(provider, ignoreCase = true) && it.modelId == modelId }

    /** Distinct provider names — used to populate the provider chips in Settings. */
    val providers: List<String> get() = all.map { it.provider }.distinct()
}
