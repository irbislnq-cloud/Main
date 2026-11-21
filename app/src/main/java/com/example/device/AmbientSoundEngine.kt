package com.example.device

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.diagnostics.AppLogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class AmbientScenePreset(
    val labelRu: String,
    val badgeIcon: String,
    val subtitleRu: String
) {
    AUTO("Авто по сцене", "✨", "Подстраивается под погоду, локацию и жанр"),
    RAIN("Ночной дождь", "🌧️", "Мягкий шум дождя и отдалённый ветер"),
    TAVERN("Таверна и камин", "🔥", "Тёплый гул очага и лёгкое потрескивание"),
    DUNGEON("Подземелье / Лес", "🕯️", "Глубокое эхо и протяжный ветер"),
    SPACE_CYBER("Космос / Кибер", "🛸", "Глубокий гармонический суб-дрон отсека"),
    COMBAT("Тревога / Бой", "⚔️", "Напряжённый низкий пульс"),
    SILENT("Тишина", "🔇", "Эмбиент отключён");

    companion object {
        fun fromString(raw: String?): AmbientScenePreset =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

object AmbientSceneResolver {
    fun resolve(
        requested: AmbientScenePreset,
        weather: String,
        locationName: String,
        locationDesc: String,
        combatActive: Boolean,
        tags: List<String> = emptyList()
    ): AmbientScenePreset {
        if (requested != AmbientScenePreset.AUTO) return requested
        if (combatActive) return AmbientScenePreset.COMBAT

        val corpus = "$weather $locationName $locationDesc ${tags.joinToString(" ")}".lowercase()
        return when {
            corpus.contains("дожд") || corpus.contains("лив") || corpus.contains("гроз") ||
                corpus.contains("шторм") || corpus.contains("туман") || corpus.contains("нуар") ->
                AmbientScenePreset.RAIN

            corpus.contains("таверн") || corpus.contains("камин") || corpus.contains("кофейн") ||
                corpus.contains("дом") || corpus.contains("трактир") || corpus.contains("лагер") ||
                corpus.contains("уют") ->
                AmbientScenePreset.TAVERN

            corpus.contains("космос") || corpus.contains("станц") || corpus.contains("орбит") ||
                corpus.contains("кибер") || corpus.contains("неон") || corpus.contains("сервер") ||
                corpus.contains("бункер") || corpus.contains("техно") ->
                AmbientScenePreset.SPACE_CYBER

            corpus.contains("бой") || corpus.contains("опасн") || corpus.contains("тревог") ||
                corpus.contains("засад") ->
                AmbientScenePreset.COMBAT

            else -> AmbientScenePreset.DUNGEON
        }
    }
}

object AmbientSoundEngine {
    private const val TAG = "AmbientSound"
    private const val SAMPLE_RATE = 22050
    private const val CHUNK_SAMPLES = 2205 // ~100ms chunks

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var synthJob: Job? = null

    @Volatile private var desiredEnabled: Boolean = false
    @Volatile private var desiredPreset: AmbientScenePreset = AmbientScenePreset.SILENT
    @Volatile private var desiredVolume: Float = 0.35f
    @Volatile private var lifecyclePaused: Boolean = false

    private val _activeResolvedPreset = MutableStateFlow(AmbientScenePreset.SILENT)
    val activeResolvedPreset: StateFlow<AmbientScenePreset> = _activeResolvedPreset.asStateFlow()

    fun updateState(
        enabled: Boolean,
        resolvedPreset: AmbientScenePreset,
        volume: Float
    ) {
        desiredEnabled = enabled
        desiredPreset = resolvedPreset
        desiredVolume = volume.coerceIn(0.02f, 1.0f)
        _activeResolvedPreset.value = if (enabled) resolvedPreset else AmbientScenePreset.SILENT
        syncPlayback()
    }

    fun onAppBackgrounded() {
        lifecyclePaused = true
        stopSynth()
    }

    fun onAppForegrounded() {
        lifecyclePaused = false
        syncPlayback()
    }

    fun release() {
        desiredEnabled = false
        desiredPreset = AmbientScenePreset.SILENT
        _activeResolvedPreset.value = AmbientScenePreset.SILENT
        stopSynth()
    }

    private fun syncPlayback() {
        val shouldPlay = desiredEnabled && !lifecyclePaused &&
            desiredPreset != AmbientScenePreset.SILENT &&
            desiredPreset != AmbientScenePreset.AUTO
        if (!shouldPlay) {
            stopSynth()
            return
        }
        if (synthJob?.isActive == true) return
        synthJob = scope.launch { runSynthLoop() }
    }

    private fun stopSynth() {
        synthJob?.cancel()
        synthJob = null
    }

    private suspend fun CoroutineScope.runSynthLoop() {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(CHUNK_SAMPLES * 4)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()

            val buffer = ShortArray(CHUNK_SAMPLES)
            var brown = 0.0
            var lowPass = 0.0
            var phaseA = 0.0
            var phaseB = 0.0
            var crackleEnv = 0.0
            var lfoPhase = 0.0
            var smoothVol = 0.0
            val rng = Random(System.currentTimeMillis())

            while (isActive && desiredEnabled && !lifecyclePaused) {
                val preset = desiredPreset
                if (preset == AmbientScenePreset.SILENT || preset == AmbientScenePreset.AUTO) break
                val targetVol = desiredVolume.toDouble() * 0.45

                for (i in 0 until CHUNK_SAMPLES) {
                    smoothVol += (targetVol - smoothVol) * 0.0025
                    val white = rng.nextDouble() * 2.0 - 1.0
                    brown = (brown + 0.028 * white).coerceIn(-1.0, 1.0)
                    lfoPhase += 2.0 * PI * 0.14 / SAMPLE_RATE
                    if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
                    val lfo = 0.72 + 0.28 * sin(lfoPhase)

                    val sample = when (preset) {
                        AmbientScenePreset.RAIN -> {
                            lowPass += 0.22 * (white * 0.42 + brown * 0.58 - lowPass)
                            val droplet = if (rng.nextInt(3200) == 0) white * 0.35 else 0.0
                            (lowPass * lfo + droplet) * 0.85
                        }
                        AmbientScenePreset.TAVERN -> {
                            lowPass += 0.06 * (brown - lowPass)
                            if (rng.nextInt(950) == 0) crackleEnv = 0.65
                            crackleEnv *= 0.992
                            val crackle = white * crackleEnv * 0.55
                            lowPass * 0.7 + crackle
                        }
                        AmbientScenePreset.DUNGEON -> {
                            phaseA += 2.0 * PI * 68.0 / SAMPLE_RATE
                            phaseB += 2.0 * PI * 102.0 / SAMPLE_RATE
                            lowPass += 0.045 * (brown - lowPass)
                            lowPass * 0.85 * lfo + (sin(phaseA) * 0.12 + sin(phaseB) * 0.06) * lfo
                        }
                        AmbientScenePreset.SPACE_CYBER -> {
                            phaseA += 2.0 * PI * 55.0 / SAMPLE_RATE
                            phaseB += 2.0 * PI * 82.4 / SAMPLE_RATE
                            val shimmer = sin(phaseA * 3.0) * 0.04 * lfo
                            sin(phaseA) * 0.28 + sin(phaseB) * 0.16 + shimmer + brown * 0.12
                        }
                        AmbientScenePreset.COMBAT -> {
                            phaseA += 2.0 * PI * 52.0 / SAMPLE_RATE
                            phaseB += 2.0 * PI * 1.8 / SAMPLE_RATE
                            val pulse = ((sin(phaseB) + 1.0) * 0.5)
                            sin(phaseA) * (0.22 + 0.30 * pulse * pulse) + brown * 0.20
                        }
                        AmbientScenePreset.AUTO, AmbientScenePreset.SILENT -> 0.0
                    }

                    val pcm = (sample * smoothVol * Short.MAX_VALUE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buffer[i] = pcm.toShort()
                }

                track.write(buffer, 0, CHUNK_SAMPLES)
            }
        } catch (t: Throwable) {
            AppLogBuffer.d(TAG, "Procedural audio playback stopped/unavailable: ${t.message}")
        } finally {
            runCatching {
                track?.pause()
                track?.flush()
                track?.stop()
                track?.release()
            }
        }
    }
}
