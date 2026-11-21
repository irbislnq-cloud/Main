package com.example.data.export

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.model.LlmProvider
import com.example.data.model.LoreEntry
import com.example.data.model.RpMode
import com.example.data.model.RpScenario
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.Inflater

data class ParsedCharacterCard(
    val sourceFormat: String,
    val scenario: RpScenario,
    val alternateGreetingsCount: Int = 0
)

object SillyTavernCardParser {

    private val pngSignature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun parseFromUri(context: Context, uri: Uri): Result<ParsedCharacterCard> {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("Не удалось прочитать выбранный файл."))
            if (bytes.size >= 8 && bytes.sliceArray(0 until 8).contentEquals(pngSignature)) {
                parsePngCardBytes(bytes, context)
            } else {
                parseJsonString(String(bytes, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parsePngCardBytes(bytes: ByteArray, context: Context? = null): Result<ParsedCharacterCard> {
        if (bytes.size < 8 || !bytes.sliceArray(0 until 8).contentEquals(pngSignature)) {
            return Result.failure(IllegalArgumentException("Файл не является корректным PNG-изображением."))
        }

        var pos = 8
        var encodedCardPayload: String? = null

        while (pos + 12 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            if (length < 0 || pos + 12 + length > bytes.size) break
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            val dataEnd = dataStart + length

            if (type == "tEXt") {
                val chunkData = bytes.sliceArray(dataStart until dataEnd)
                val nullIdx = chunkData.indexOf(0.toByte())
                if (nullIdx > 0) {
                    val key = String(chunkData, 0, nullIdx, Charsets.ISO_8859_1)
                    if (key.equals("chara", ignoreCase = true) || key.equals("ccv3", ignoreCase = true)) {
                        encodedCardPayload = String(chunkData, nullIdx + 1, chunkData.size - nullIdx - 1, Charsets.ISO_8859_1)
                        break
                    }
                }
            } else if (type == "zTXt") {
                val chunkData = bytes.sliceArray(dataStart until dataEnd)
                val nullIdx = chunkData.indexOf(0.toByte())
                if (nullIdx > 0 && nullIdx + 2 <= chunkData.size) {
                    val key = String(chunkData, 0, nullIdx, Charsets.ISO_8859_1)
                    val compMethod = chunkData[nullIdx + 1].toInt()
                    if ((key.equals("chara", ignoreCase = true) || key.equals("ccv3", ignoreCase = true)) && compMethod == 0) {
                        val compressed = chunkData.sliceArray(nullIdx + 2 until chunkData.size)
                        encodedCardPayload = inflateZlibToString(compressed, Charsets.ISO_8859_1)
                        break
                    }
                }
            } else if (type == "iTXt") {
                val chunkData = bytes.sliceArray(dataStart until dataEnd)
                val parsed = parseITXtChunk(chunkData)
                if (parsed != null && (parsed.first.equals("chara", ignoreCase = true) || parsed.first.equals("ccv3", ignoreCase = true))) {
                    encodedCardPayload = parsed.second
                    break
                }
            } else if (type == "IEND") {
                break
            }

            pos += 12 + length
        }

        val b64 = encodedCardPayload?.trim()
            ?: return Result.failure(IllegalArgumentException("В PNG не найдена карточка персонажа (чанк 'chara' / 'ccv3')."))

        val decodedJson = try {
            val decodedBytes = Base64.decode(b64, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            if (b64.trimStart().startsWith("{")) b64
            else return Result.failure(IllegalArgumentException("Не удалось декодировать Base64 карточки: ${e.localizedMessage}"))
        }

        return parseJsonString(decodedJson, defaultSource = "SillyTavern PNG").map { parsed ->
            val savedPortrait = if (context != null) {
                com.example.data.local.SceneArtManager.saveCardPngBytesSync(context, bytes, "st_card").orEmpty()
            } else {
                ""
            }
            if (savedPortrait.isNotBlank()) {
                parsed.copy(scenario = parsed.scenario.copy(portraitUri = savedPortrait))
            } else {
                parsed
            }
        }
    }

    fun parseJsonString(
        rawJson: String,
        defaultSource: String = "Chub / Tavern JSON"
    ): Result<ParsedCharacterCard> {
        return try {
            val root = JSONObject(rawJson.trim())
            val spec = root.optString("spec")

            // Check if LoreCraft native scenario format
            if (root.has("rpMode") && root.has("systemPrompt") && root.has("title")) {
                val scenario = JsonExportManager.scenarioFromJson(root)
                return Result.success(ParsedCharacterCard("LoreCraft Сценарий", scenario, 0))
            }

            // V2 Card has spec == "chara_card_v2" / "chara_card_v3" and nested "data" object
            val cardObj = if ((spec.startsWith("chara_card") || root.has("data")) && root.optJSONObject("data") != null) {
                root.getJSONObject("data")
            } else {
                root
            }

            val name = cardObj.optString("name").ifBlank {
                root.optString("name", "Импортированный персонаж")
            }.trim().take(120)

            val description = cardObj.optString("description").trim()
            val personality = cardObj.optString("personality").trim()
            val scenarioText = cardObj.optString("scenario").trim()
            val firstMessage = cardObj.optString("first_mes").ifBlank {
                cardObj.optString("firstMessage")
            }.trim().take(8000)
            val mesExample = cardObj.optString("mes_example").trim().take(8000)
            val customSystemPrompt = cardObj.optString("system_prompt").trim()
            val creatorNotes = cardObj.optString("creator_notes").trim()

            val combinedBio = buildString {
                if (description.isNotBlank()) appendLine(description)
                if (personality.isNotBlank()) {
                    appendLine("\n[Характер]")
                    appendLine(personality)
                }
                if (scenarioText.isNotBlank()) {
                    appendLine("\n[Контекст сцены]")
                    appendLine(scenarioText)
                }
            }.trim().take(8000)

            val resolvedSystemPrompt = if (customSystemPrompt.isNotBlank()) {
                customSystemPrompt
            } else {
                buildString {
                    appendLine("Ты играешь роль $name.")
                    appendLine("Описывай действия в *звёздочках*, а реплики — в «кавычках». Не управляй действиями {{user}}.")
                    if (personality.isNotBlank()) {
                        appendLine("Черты характера: $personality")
                    }
                    if (scenarioText.isNotBlank()) {
                        appendLine("Ситуация: $scenarioText")
                    }
                }.trim()
            }.take(20000)

            val tagsArr = cardObj.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    val t = tagsArr.optString(i).trim().take(40)
                    if (t.isNotEmpty() && tags.size < 10) tags.add(t)
                }
            }

            val altArr = cardObj.optJSONArray("alternate_greetings")
            val altCount = altArr?.length() ?: 0

            val loreEntries = mutableListOf<LoreEntry>()
            val charBook = cardObj.optJSONObject("character_book")
            val bookEntries = charBook?.optJSONArray("entries")
            if (bookEntries != null) {
                for (i in 0 until bookEntries.length()) {
                    if (loreEntries.size >= 40) break
                    val e = bookEntries.optJSONObject(i) ?: continue
                    val content = e.optString("content").trim().take(1500)
                    if (content.isEmpty()) continue
                    val keys = mutableListOf<String>()
                    val keysArr = e.optJSONArray("keys")
                    if (keysArr != null) {
                        for (k in 0 until keysArr.length()) {
                            val ks = keysArr.optString(k).trim().take(80)
                            if (ks.isNotEmpty() && keys.size < 20) keys.add(ks)
                        }
                    }
                    val entryTitle = e.optString("name").ifBlank {
                        e.optString("comment").ifBlank { keys.firstOrNull().orEmpty() }
                    }.trim().take(100)
                    val constant = e.optBoolean("constant", false)
                    val enabled = e.optBoolean("enabled", true)
                    val priority = e.optLong("priority", 10L).coerceIn(1L, 100L)
                    loreEntries.add(
                        LoreEntry(
                            id = UUID.randomUUID().toString().take(16),
                            title = entryTitle,
                            keys = keys,
                            content = content,
                            alwaysActive = constant,
                            useRegex = false,
                            priority = priority,
                            enabled = enabled
                        )
                    )
                }
            }

            val subtitle = creatorNotes.lineSequence().firstOrNull()?.trim()?.take(180)
                ?: scenarioText.lineSequence().firstOrNull()?.trim()?.take(180)
                ?: "Импортировано ($defaultSource)"

            val scenario = RpScenario(
                title = name,
                subtitle = subtitle,
                rpMode = RpMode.CHARACTER_1ON1.name,
                systemPrompt = resolvedSystemPrompt,
                characterBio = combinedBio,
                firstMessage = firstMessage,
                exampleDialogue = mesExample,
                loreEntries = loreEntries,
                participants = listOf(name.take(100)),
                tags = tags.ifEmpty { listOf("Карточка") },
                defaultProvider = LlmProvider.GEMINI_AI_LOGIC.name,
                defaultModelId = "gemini-3.8-flash"
            )
            val detectedFormat = if (spec.isNotBlank() && defaultSource != "SillyTavern PNG") {
                "Chub / Card v2"
            } else {
                defaultSource
            }
            Result.success(ParsedCharacterCard(detectedFormat, scenario, altCount))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Не удалось разобрать карточку: ${e.localizedMessage}"))
        }
    }

    private fun parseLoreCraftScenario(obj: JSONObject): RpScenario {
        val gen = obj.optJSONObject("generationParams")
        val loreEntries = mutableListOf<LoreEntry>()
        val entriesArr = obj.optJSONArray("loreEntries")
        if (entriesArr != null) {
            for (i in 0 until entriesArr.length()) {
                val e = entriesArr.optJSONObject(i) ?: continue
                val keysArr = e.optJSONArray("keys")
                val keys = (0 until (keysArr?.length() ?: 0)).mapNotNull { idx ->
                    keysArr?.optString(idx)?.takeIf { it.isNotBlank() }
                }
                loreEntries.add(
                    LoreEntry(
                        id = e.optString("id").ifBlank { UUID.randomUUID().toString().take(16) },
                        title = e.optString("title", ""),
                        keys = keys,
                        content = e.optString("content", ""),
                        alwaysActive = e.optBoolean("alwaysActive", false),
                        useRegex = e.optBoolean("useRegex", false),
                        priority = e.optLong("priority", 10L).coerceIn(1L, 100L),
                        enabled = e.optBoolean("enabled", true)
                    )
                )
            }
        }
        return RpScenario(
            title = obj.optString("title", "Сценарий").ifBlank { "Сценарий" }.take(120),
            subtitle = obj.optString("subtitle", "").take(200),
            rpMode = RpMode.fromString(obj.optString("rpMode")).name,
            systemPrompt = obj.optString("systemPrompt", "Ты — ролевой партнёр.").take(20000),
            characterBio = obj.optString("characterBio", "").take(8000),
            firstMessage = obj.optString("firstMessage", "").take(8000),
            exampleDialogue = obj.optString("exampleDialogue", "").take(8000),
            loreNotes = jsonArrStrings(obj.optJSONArray("loreNotes")),
            loreEntries = loreEntries.take(40),
            participants = jsonArrStrings(obj.optJSONArray("participants")),
            tags = jsonArrStrings(obj.optJSONArray("tags")),
            defaultProvider = LlmProvider.fromString(obj.optString("defaultProvider")).name,
            defaultModelId = obj.optString("defaultModelId", "gemini-3.8-flash"),
            temperature = (gen?.optDouble("temperature") ?: obj.optDouble("temperature", 0.85)).coerceIn(0.0, 2.0),
            topP = (gen?.optDouble("topP") ?: obj.optDouble("topP", 0.95)).coerceIn(0.05, 1.0),
            topK = (gen?.optLong("topK") ?: obj.optLong("topK", 40L)).coerceIn(1L, 200L),
            maxTokens = (gen?.optLong("maxTokens") ?: obj.optLong("maxTokens", 2048L)).coerceIn(64L, 16384L),
            contextWindowMessages = (gen?.optLong("contextWindowMessages") ?: 24L).coerceIn(2L, 200L)
        )
    }

    private fun jsonArrStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx)?.takeIf { it.isNotBlank() }
        }
    }

    private fun parseITXtChunk(data: ByteArray): Pair<String, String>? {
        val kEnd = data.indexOf(0.toByte())
        if (kEnd <= 0 || kEnd + 3 >= data.size) return null
        val key = String(data, 0, kEnd, Charsets.ISO_8859_1)
        val compressionFlag = data[kEnd + 1].toInt()
        val compressionMethod = data[kEnd + 2].toInt()
        var idx = kEnd + 3
        // Skip language tag (\0)
        while (idx < data.size && data[idx] != 0.toByte()) idx++
        idx++
        // Skip translated keyword (\0)
        while (idx < data.size && data[idx] != 0.toByte()) idx++
        idx++
        if (idx >= data.size) return null
        val payload = data.sliceArray(idx until data.size)
        val text = if (compressionFlag == 1 && compressionMethod == 0) {
            inflateZlibToString(payload, Charsets.UTF_8)
        } else {
            String(payload, Charsets.UTF_8)
        }
        return key to text
    }

    private fun inflateZlibToString(compressed: ByteArray, charset: java.nio.charset.Charset): String {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput()) break
                } else {
                    out.write(buffer, 0, count)
                }
            }
            String(out.toByteArray(), charset)
        } finally {
            inflater.end()
        }
    }
}
