package com.adaptiveui.animeapp.core.network.openai.dto

import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible chat completion request. `response_format` is sent only when the provider
 * supports JSON mode — the repository omits it (null) when the model is unknown/unsupported.
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    val response_format: ResponseFormat? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    val message: ChatMessage? = null,
    val finish_reason: String? = null
)
