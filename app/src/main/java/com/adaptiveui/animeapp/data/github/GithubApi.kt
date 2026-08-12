package com.adaptiveui.animeapp.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal GitHub REST API client for the AI-generated UI build loop.
 *
 * Used by [com.adaptiveui.animeapp.ai.AiService] to push generated Compose `.kt` files to the
 * repo (Contents API) and trigger the `build-generated.yml` workflow (workflow_dispatch).
 *
 * All calls use OkHttp directly (no Retrofit) + kotlinx.serialization for JSON parsing.
 *
 * ## Credentials
 * The token + repo are hardcoded build-time constants. This is a personal demo app, NOT a
 * shipping product. The fine-grained PAT (scoped to `testplay-byte/android-adaptive-ui` only)
 * has `contents:write` + `actions:write` permissions — exactly what this client needs.
 *
 * ## Error model
 * Every public method returns `Result<T>` so the ViewModel can surface failures to the user
 * without leaking HTTP exceptions. The `Result.failure` message always includes the HTTP
 * status code + the first 500 chars of the response body for diagnostics.
 *
 * ## API references
 * - Contents API: https://docs.github.com/en/rest/repos/contents
 * - workflow_dispatch: https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event
 * - Get a workflow run: https://docs.github.com/en/rest/actions/workflow-runs#get-a-workflow-run
 */
@Singleton
class GithubApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {

    /**
     * Write (create or update) a file in the repo via the Contents API.
     *
     * If the file already exists, GETs it first to obtain the required `sha`, then PUTs with
     * that sha. Returns the commit's `html_url` on success (or a constructed URL if GitHub
     * omits it).
     */
    suspend fun writeFile(path: String, content: String, message: String, token: String, branch: String = "main"): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val existingSha = getExistingSha(path, token, branch)
                val body = buildJsonObject {
                    put("message", message)
                    put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
                    put("branch", branch)
                    if (existingSha != null) put("sha", existingSha)
                }.toString().toRequestBody(JSON_MEDIA)

                val request = Request.Builder()
                    .url("$API_BASE/repos/$REPO_OWNER/$REPO_NAME/contents/$path")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .put(body)
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string()?.take(500).orEmpty()
                        throw IllegalStateException(
                            "GitHub writeFile failed: HTTP ${resp.code} ${resp.message}. $errBody"
                        )
                    }
                    val responseStr = resp.body?.string().orEmpty()
                    val parsed = runCatching { json.parseToJsonElement(responseStr).jsonObject }.getOrNull()
                        ?: JsonObject(emptyMap())
                    val commit = parsed["commit"]?.jsonObject
                    commit?.get("html_url")?.jsonPrimitive?.contentOrNull
                        ?: "https://github.com/$REPO_OWNER/$REPO_NAME/commit/${commit?.get("sha")?.jsonPrimitive?.contentOrNull ?: "unknown"}"
                }
            }
        }

    /**
     * Trigger a GitHub Actions workflow via workflow_dispatch.
     *
     * Returns the run URL. Per the late-2024 GitHub API change, the dispatch response may
     * contain `{workflow_run_id, run_url, html_url}`; if it does, we return `html_url`.
     * Older responses are 204 No Content — we fall back to the workflow's runs page URL.
     */
    suspend fun triggerWorkflow(
        workflowFileName: String,
        token: String,
        branch: String = "main",
        inputs: Map<String, String> = emptyMap()
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Build the dispatch body manually so we can inline the `inputs` object cleanly.
                val finalBody = buildString {
                    append('{')
                    append("\"ref\":").append(jsonQuote(branch))
                    if (inputs.isNotEmpty()) {
                        append(",\"inputs\":{")
                        inputs.entries.joinTo(this, ",") { (k, v) ->
                            jsonQuote(k) + ":" + jsonQuote(v)
                        }
                        append('}')
                    }
                    append('}')
                }.toRequestBody(JSON_MEDIA)

                val request = Request.Builder()
                    .url("$API_BASE/repos/$REPO_OWNER/$REPO_NAME/actions/workflows/$workflowFileName/dispatches")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .post(finalBody)
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string()?.take(500).orEmpty()
                        throw IllegalStateException(
                            "GitHub triggerWorkflow failed: HTTP ${resp.code} ${resp.message}. $errBody"
                        )
                    }
                    val responseStr = resp.body?.string().orEmpty()
                    if (responseStr.isBlank()) {
                        // Older 204 No Content response — return a generic runs URL.
                        return@use "https://github.com/$REPO_OWNER/$REPO_NAME/actions/workflows/$workflowFileName"
                    }
                    val parsed = runCatching { json.parseToJsonElement(responseStr).jsonObject }.getOrNull()
                    parsed?.get("html_url")?.jsonPrimitive?.contentOrNull
                        ?: parsed?.get("run_url")?.jsonPrimitive?.contentOrNull
                        ?: "https://github.com/$REPO_OWNER/$REPO_NAME/actions/workflows/$workflowFileName"
                }
            }
        }

    /**
     * Fetch the status of a workflow run by ID.
     *
     * `status` values: `queued` | `in_progress` | `completed`.
     * `conclusion` is null until the run finishes, then one of: `success` | `failure` |
     * `cancelled` | `neutral` | `skipped` | `timed_out` | `action_required`.
     */
    suspend fun getRunStatus(runId: Long, token: String): Result<RunStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$API_BASE/repos/$REPO_OWNER/$REPO_NAME/actions/runs/$runId")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .get()
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string()?.take(500).orEmpty()
                        throw IllegalStateException(
                            "GitHub getRunStatus failed: HTTP ${resp.code} ${resp.message}. $errBody"
                        )
                    }
                    val parsed = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    RunStatus(
                        status = parsed["status"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                        conclusion = parsed["conclusion"]?.jsonPrimitive?.contentOrNull,
                        htmlUrl = parsed["html_url"]?.jsonPrimitive?.contentOrNull
                            ?: "https://github.com/$REPO_OWNER/$REPO_NAME/actions/runs/$runId"
                    )
                }
            }
        }

    /**
     * GET the file at [path] and return its current `sha` if it exists, or null if 404 /
     * any other error (treat as "doesn't exist yet — we'll create it").
     *
     * This is a synchronous blocking call (used only inside `withContext(Dispatchers.IO)`
     * via [writeFile]); not marked `suspend` so it can sit cleanly inline.
     */
    private fun getExistingSha(path: String, token: String, branch: String): String? {
        val request = Request.Builder()
            .url("$API_BASE/repos/$REPO_OWNER/$REPO_NAME/contents/$path?ref=$branch")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (resp.code == 404) return null
                if (!resp.isSuccessful) return null
                val parsed = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                parsed["sha"]?.jsonPrimitive?.contentOrNull
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun jsonQuote(s: String): String {
        // Use the injected Json instance to safely escape JSON strings. The resulting string
        // includes the surrounding quotes.
        return json.encodeToString(
            kotlinx.serialization.json.JsonPrimitive.serializer(),
            kotlinx.serialization.json.JsonPrimitive(s)
        )
    }

    companion object {
        private const val API_BASE = "https://api.github.com"
        private const val REPO_OWNER = "testplay-byte"
        private const val REPO_NAME = "android-adaptive-ui"

        // Hardcoded fine-grained PAT scoped to this repo only (contents:write + actions:write).
        // This is a demo app — the token is intentionally baked in for the build loop to work
        // end-to-end without runtime user configuration.

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Status snapshot for a single workflow run.
 *
 * @property status One of `queued` | `in_progress` | `completed`.
 * @property conclusion Null until the run finishes; then `success` | `failure` | `cancelled` | etc.
 * @property htmlUrl Browser-visible URL of the run page.
 */
data class RunStatus(
    val status: String,
    val conclusion: String?,
    val htmlUrl: String
)
