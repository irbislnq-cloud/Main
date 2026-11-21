package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.diagnostics.AppLogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

object SceneArtManager {
    private const val TAG = "SceneArtManager"
    private const val ART_DIR = "scene_art"
    private const val MAX_DIM_PX = 1280

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    private fun artDir(context: Context): File =
        File(context.applicationContext.filesDir, ART_DIR).apply { mkdirs() }

    suspend fun importImageFromUri(context: Context, uri: Uri, prefix: String = "art"): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Не удалось открыть выбранное изображение.")
                saveCompressedBytes(context, bytes, prefix)
            }
        }

    fun saveCardPngBytesSync(context: Context, pngBytes: ByteArray, prefix: String = "card"): String? {
        return runCatching {
            saveCompressedBytes(context, pngBytes, prefix)
        }.onFailure {
            AppLogBuffer.w(TAG, "Failed to persist PNG card portrait", it)
        }.getOrNull()
    }

    suspend fun generateFromFreeOrCustomEndpoint(
        context: Context,
        prompt: String,
        endpointTemplate: String,
        aspectHint: String = "portrait"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPrompt = prompt.trim().ifBlank { "fantasy dark atmospheric character scene, painterly" }
            val encoded = URLEncoder.encode(cleanPrompt, "UTF-8")
            val dims = if (aspectHint == "backdrop") "width=1024&height=576" else "width=768&height=768"
            val template = endpointTemplate.trim().ifBlank {
                "https://image.pollinations.ai/prompt/{prompt}?$dims&nologo=true"
            }
            val url = if (template.contains("{prompt}")) {
                template.replace("{prompt}", encoded)
            } else {
                "${template.trimEnd('/')}/$encoded?$dims&nologo=true"
            }
            AppLogBuffer.i(TAG, "Requesting scene image ($aspectHint) from endpoint")
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful || body == null) {
                    throw IllegalStateException("HTTP ${resp.code} при получении изображения")
                }
                val bytes = body.bytes()
                if (bytes.size < 512) {
                    throw IllegalStateException("Получен слишком маленький или пустой файл изображения")
                }
                saveCompressedBytes(context, bytes, "gen_$aspectHint")
            }
        }
    }

    private fun saveCompressedBytes(context: Context, rawBytes: ByteArray, prefix: String): String {
        val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, optsBounds)

        var sampleSize = 1
        val maxSide = maxOf(optsBounds.outWidth, optsBounds.outHeight)
        if (maxSide > MAX_DIM_PX) {
            var half = maxSide / 2
            while (half / sampleSize >= MAX_DIM_PX) {
                sampleSize *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: throw IllegalStateException("Файл не распознан как изображение.")

        val outFile = File(artDir(context), "${prefix}_${UUID.randomUUID().toString().take(12)}.jpg")
        FileOutputStream(outFile).use { fos ->
            decoded.compress(Bitmap.CompressFormat.JPEG, 88, fos)
        }
        decoded.recycle()
        AppLogBuffer.d(TAG, "Saved scene art: ${outFile.name}")
        return outFile.toURI().toString()
    }
}
