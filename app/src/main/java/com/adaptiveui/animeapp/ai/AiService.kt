package com.adaptiveui.animeapp.ai

import com.adaptiveui.animeapp.core.network.openai.OpenAiApi
import com.adaptiveui.animeapp.core.network.openai.dto.ChatMessage
import com.adaptiveui.animeapp.core.network.openai.dto.ChatRequest
import com.adaptiveui.animeapp.core.network.openai.dto.ResponseFormat
import com.adaptiveui.animeapp.data.github.GithubApi
import com.adaptiveui.animeapp.domain.model.AiSettings
import com.adaptiveui.animeapp.interpreter.ScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the two-capability AI system:
 *
 * 1. **Live preview** — [generateScreenSpec] calls the OpenAI-compatible chat endpoint with JSON
 *    mode and parses the response into a [ScreenSpec] that [com.adaptiveui.animeapp.interpreter.UiSpecInterpreter]
 *    renders instantly (no compilation).
 * 2. **Permanent build** — [generateComposeFile] asks the same model for a raw Kotlin Compose
 *    file; [pushAndTriggerBuild] then writes that file to the repo via the GitHub Contents API
 *    and dispatches the `build-generated.yml` workflow to compile an APK.
 *
 * The class is provider-agnostic: any OpenAI-compatible `/chat/completions` endpoint works
 * (Groq, Gemini OpenAI-compat, Cerebras, OpenRouter, Together, Mistral, SambaNova, etc.). The
 * base URL + API key + model ID come from [AiSettings].
 *
 * Every public method returns `Result<T>` so the ViewModel can surface failures cleanly. The
 * [testConnection] helper returns a [TestResult] (success flag + human message) for the Settings
 * "Test connection" button.
 */
@Singleton
class AiService @Inject constructor(
    private val openAiApi: OpenAiApi,
    private val json: Json,
    private val githubApi: GithubApi
) {

    /**
     * Generate a [ScreenSpec] JSON for live preview. Returns the parsed spec on success.
     *
     * Flow:
     *  1. Build a system prompt via [SystemPrompt.buildForSpec] that documents the full ScreenSpec
     *     schema + data binding + color extraction + examples.
     *  2. POST to `${settings.baseUrl}/chat/completions` with `response_format = json_object`.
     *  3. Strip optional markdown fences (in case the model wraps the JSON anyway).
     *  4. Decode the content as [ScreenSpec] via kotlinx.serialization.
     */
    suspend fun generateScreenSpec(
        settings: AiSettings,
        userInstruction: String,
        screenName: String,
        availableData: String
    ): Result<ScreenSpec> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.isConfigured) {
                "AI is not configured — paste an API key and select a model in Settings first."
            }
            require(userInstruction.isNotBlank()) { "Instruction must not be empty." }

            val systemPrompt = SystemPrompt.buildForSpec(screenName, availableData) +
                "\n\n" + SystemPrompt.JSON_INSTRUCTION

            val request = ChatRequest(
                model = settings.effectiveModelId,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userInstruction)
                ),
                temperature = settings.temperature,
                response_format = ResponseFormat(type = "json_object")
            )

            val url = "${settings.baseUrl.trimEnd('/')}/chat/completions"
            val auth = "Bearer ${settings.apiKey}"

            val response = openAiApi.chat(url = url, auth = auth, body = request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("AI returned no message content.")

            val cleaned = stripMarkdownFences(content)
            try {
                json.decodeFromString(ScreenSpec.serializer(), cleaned)
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Could not parse AI output as ScreenSpec JSON: ${t.message}. " +
                        "First 300 chars: ${cleaned.take(300)}",
                    t
                )
            }
        }
    }

    /**
     * Generate a real Compose `.kt` file for permanent compilation. Returns the Kotlin source
     * as a string. The source defines a single
     * `@Composable fun GeneratedScreen(data: ScreenData, modifier: Modifier)` function in the
     * `com.adaptiveui.animeapp.generated` package.
     *
     * No JSON mode here — the model is asked for raw Kotlin code. The model is instructed to
     * not wrap the output in markdown fences, but we strip them defensively just in case.
     */
    suspend fun generateComposeFile(
        settings: AiSettings,
        userInstruction: String,
        screenName: String,
        availableData: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.isConfigured) {
                "AI is not configured — paste an API key and select a model in Settings first."
            }
            require(userInstruction.isNotBlank()) { "Instruction must not be empty." }

            val systemPrompt = SystemPrompt.buildForCompose(screenName, availableData)

            val request = ChatRequest(
                model = settings.effectiveModelId,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userInstruction)
                ),
                temperature = settings.temperature,
                response_format = null // raw code, no JSON mode
            )

            val url = "${settings.baseUrl.trimEnd('/')}/chat/completions"
            val auth = "Bearer ${settings.apiKey}"

            val response = openAiApi.chat(url = url, auth = auth, body = request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("AI returned no message content.")

            stripMarkdownFences(content)
        }
    }

    /**
     * Push a generated Compose file to the repo and trigger the `build-generated.yml` workflow.
     *
     * The file is written to `app/src/main/java/com/adaptiveui/animeapp/generated/{ScreenName}Screen.kt`
     * (the [screenName] is sanitized to a PascalCase Kotlin identifier suffix). After the file
     * is committed, the workflow is dispatched with `inputs: { screen_name: <sanitized> }` so
     * the ntfy notification can mention which screen was rebuilt.
     *
     * Returns the GitHub Actions run URL on success. If the file push succeeds but the workflow
     * dispatch fails, the error message includes the commit URL so the user can recover.
     */
    suspend fun pushAndTriggerBuild(composeSource: String, screenName: String, githubToken: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = sanitizeScreenName(screenName)
                val path = "app/src/main/java/com/adaptiveui/animeapp/generated/${sanitized}Screen.kt"
                val commitMessage = "chore(ai): regenerate $sanitized screen via AI"

                val commitUrl = githubApi.writeFile(path, composeSource, commitMessage, githubToken).getOrElse { err ->
                    throw IllegalStateException("Failed to push generated file: ${err.message}", err)
                }

                githubApi.triggerWorkflow(
                    workflowFileName = "build-generated.yml",
                    token = githubToken,
                    inputs = mapOf("screen_name" to sanitized)
                ).getOrElse { err ->
                    throw IllegalStateException(
                        "File pushed ($commitUrl) but workflow dispatch failed: ${err.message}", err
                    )
                }
            }
        }

    /**
     * Test the AI configuration by sending a minimal chat request ("Reply with exactly: OK").
     * Returns a [TestResult] indicating success/failure with a human-readable message.
     * Used by the Settings "Test connection" button.
     */
    suspend fun testConnection(settings: AiSettings): TestResult = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            return@withContext TestResult(false, "Add an API key and select a model first.")
        }
        val request = ChatRequest(
            model = settings.effectiveModelId,
            messages = listOf(
                ChatMessage(role = "system", content = "Reply with exactly: OK"),
                ChatMessage(role = "user", content = "ping")
            ),
            temperature = 0.0,
            response_format = null
        )
        val url = "${settings.baseUrl.trimEnd('/')}/chat/completions"
        val auth = "Bearer ${settings.apiKey}"
        try {
            val response = openAiApi.chat(url = url, auth = auth, body = request)
            val content = response.choices.firstOrNull()?.message?.content
            if (content != null) {
                TestResult(true, "Connection successful. Model replied: \"${content.take(50)}\"")
            } else {
                TestResult(false, "Connected but received an empty response.")
            }
        } catch (t: HttpException) {
            TestResult(false, httpExceptionMessage(t))
        } catch (t: UnknownHostException) {
            TestResult(false, "Cannot reach the server. Check your internet connection and base URL.")
        } catch (t: SocketTimeoutException) {
            TestResult(false, "Request timed out. The provider may be slow or unreachable.")
        } catch (t: Throwable) {
            TestResult(false, "Error: ${t.message ?: t::class.simpleName}")
        }
    }

    /**
     * Strip optional ```kotlin ... ``` or ```json ... ``` fences if the model wrapped its
     * output despite instructions. Also trims leading/trailing whitespace.
     */
    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        // Drop the opening fence line (may include language tag like `json` or `kotlin`).
        val afterOpen = trimmed.substringAfter("\n", "")
        // Drop the trailing fence if present.
        val withoutClose = afterOpen.substringBeforeLast("```", afterOpen)
        return withoutClose.trim()
    }

    /**
     * Sanitize a screen name into a PascalCase Kotlin identifier suffix suitable for use in
     * a file name like `{PascalCase}Screen.kt` and a class-internal label.
     *
     * "Home" → "Home", "search" → "Search", "anime-details" → "AnimeDetails",
     * "" → "Generated".
     */
    private fun sanitizeScreenName(screenName: String): String {
        val pascal = screenName.trim()
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("") { word ->
                word.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
        return if (pascal.isNotBlank()) pascal else "Generated"
    }

    private fun httpExceptionMessage(t: HttpException): String {
        val code = t.code()
        return when (code) {
            401 -> "Authentication failed (401). Check your API key."
            403 -> "Forbidden (403). Your key may not have access to this model."
            404 -> "Not found (404). Check the base URL and model ID."
            429 -> "Rate limited (429). Wait a moment and try again."
            in 500..599 -> "Server error ($code). The provider may be having issues."
            else -> "HTTP $code: ${t.message()}"
        }
    }
}

/**
 * Result of a [AiService.testConnection] call. Surfaced verbatim in the Settings UI.
 */
data class TestResult(val success: Boolean, val message: String)
