package com.adaptiveui.animeapp.core.network.openai

import com.adaptiveui.animeapp.core.network.openai.dto.ChatRequest
import com.adaptiveui.animeapp.core.network.openai.dto.ChatResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit interface for any OpenAI-compatible `/v1/chat/completions` endpoint
 * (Groq, Gemini OpenAI-compat, Cerebras, OpenRouter, Together, Mistral, SambaNova, etc.).
 *
 * We use `@Url` so the base URL is supplied per-call by [com.adaptiveui.animeapp.data.repository.AiRepository]
 * — this lets the user switch providers without rebuilding the Retrofit instance. The
 * `Authorization` header is `"Bearer <api key>"` (set by the repository).
 */
interface OpenAiApi {

    @POST
    suspend fun chat(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body body: ChatRequest
    ): ChatResponse
}
