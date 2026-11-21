package com.example.data.export

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PublishedGistResult(
    val gistId: String,
    val htmlUrl: String,
    val rawUrl: String
)

object GitHubGistService {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    suspend fun publishGist(
        token: String,
        description: String,
        fileName: String,
        jsonContent: String,
        isPublic: Boolean
    ): Result<PublishedGistResult> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Укажите локальный GitHub Token (scope: gist) для публикации в Gist.")
            )
        }
        try {
            val payload = JSONObject().apply {
                put("description", description.ifBlank { "LoreCraft RP Studio Bundle" })
                put("public", isPublic)
                put("files", JSONObject().apply {
                    put(fileName, JSONObject().apply {
                        put("content", jsonContent)
                    })
                })
            }
            val request = Request.Builder()
                .url("https://api.github.com/gists")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer $cleanToken")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            withTimeout(35_000L) {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val msg = try {
                            JSONObject(bodyStr).optString("message").ifBlank { bodyStr.take(200) }
                        } catch (_: Exception) {
                            bodyStr.take(200)
                        }
                        return@withTimeout Result.failure(
                            IllegalStateException("GitHub API ${response.code}: $msg")
                        )
                    }
                    val json = JSONObject(bodyStr)
                    val id = json.optString("id")
                    val htmlUrl = json.optString("html_url")
                    val filesObj = json.optJSONObject("files")
                    val rawUrl = filesObj?.optJSONObject(fileName)?.optString("raw_url")
                        ?: htmlUrl
                    Result.success(PublishedGistResult(id, htmlUrl, rawUrl))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTextFromGitHubOrUrl(
        rawInputUrl: String,
        optionalToken: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = rawInputUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("Укажите корректную https:// ссылку."))
        }
        try {
            // 1. Check if this is a GitHub Gist page URL (e.g. https://gist.github.com/user/gist_id)
            val gistMatch = Regex("""^https?://gist\.github\.com/(?:[^/]+/)?([a-fA-F0-9]{10,48})(?:/.*)?$""").find(url)
            if (gistMatch != null) {
                val gistId = gistMatch.groupValues[1]
                val apiReq = Request.Builder()
                    .url("https://api.github.com/gists/$gistId")
                    .header("Accept", "application/vnd.github+json")
                    .apply {
                        if (optionalToken.isNotBlank()) {
                            header("Authorization", "Bearer ${optionalToken.trim()}")
                        }
                    }
                    .build()
                return@withContext withTimeout(30_000L) {
                    client.newCall(apiReq).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            return@use Result.failure(IllegalStateException("Ошибка Gist API (${resp.code})"))
                        }
                        val json = JSONObject(body)
                        val files = json.optJSONObject("files")
                            ?: return@use Result.failure(IllegalStateException("В Gist не найдено файлов."))
                        val keys = files.keys()
                        var chosenText: String? = null
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val f = files.optJSONObject(k) ?: continue
                            val content = f.optString("content")
                            if (k.endsWith(".json", ignoreCase = true) || chosenText == null) {
                                if (content.isNotBlank()) chosenText = content
                                if (k.endsWith(".json", ignoreCase = true)) break
                            }
                        }
                        if (chosenText.isNullOrBlank()) {
                            Result.failure(IllegalStateException("Файл в Gist пуст."))
                        } else {
                            Result.success(chosenText)
                        }
                    }
                }
            }

            // 2. Convert normal GitHub blob URL to raw.githubusercontent.com
            val targetUrl = convertGitHubBlobToRaw(url)
            val req = Request.Builder()
                .url(targetUrl)
                .apply {
                    if (optionalToken.isNotBlank() && targetUrl.contains("github", ignoreCase = true)) {
                        header("Authorization", "Bearer ${optionalToken.trim()}")
                    }
                }
                .build()

            withTimeout(30_000L) {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(IllegalStateException("HTTP ${resp.code} при загрузке URL"))
                    } else if (body.isBlank()) {
                        Result.failure(IllegalStateException("Загруженный файл пуст."))
                    } else {
                        Result.success(body)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun convertGitHubBlobToRaw(url: String): String {
        val blobRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$""")
        val m = blobRegex.find(url) ?: return url
        val owner = m.groupValues[1]
        val repo = m.groupValues[2]
        val branch = m.groupValues[3]
        val path = m.groupValues[4]
        return "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
    }
}
