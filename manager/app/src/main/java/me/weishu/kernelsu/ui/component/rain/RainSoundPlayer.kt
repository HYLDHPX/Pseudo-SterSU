package me.weishu.kernelsu.ui.component.rain

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.RuntimeAudioCoordinator
import me.weishu.kernelsu.ui.util.CUSTOM_AUDIO_SETTINGS_KEY
import me.weishu.kernelsu.ui.util.isAudioPlaybackAllowed
import me.weishu.kernelsu.ui.util.readAppAudioSettings
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesizes ambient rain from filtered noise, so the style ships without a large asset
 * and the loudness can follow the scene band by band.
 */
internal object RainNoise {
    const val SAMPLE_RATE = 22_050
    /** Loop length: long enough that the seam is not audible, short enough to stay cheap. */
    const val LOOP_SECONDS = 8
    private const val CHANNELS = 1

    /** Brown-ish noise gives rain body; a touch of pink keeps it from sounding like a hiss. */
    fun render(sound: RainSound, seed: Int = 0x5A17): ShortArray {
        val frames = SAMPLE_RATE * LOOP_SECONDS
        val random = Random(seed + sound.ordinal * 7919)
        val lowPass = when (sound) {
            RainSound.Off, RainSound.Whisper -> 0.94f
            RainSound.Shower -> 0.88f
            RainSound.Downpour -> 0.78f
        }
        val gain = when (sound) {
            RainSound.Off -> 0f
            RainSound.Whisper -> 0.22f
            RainSound.Shower -> 0.34f
            RainSound.Downpour -> 0.44f
        }
        val body = FloatArray(frames)
        var previous = 0f
        for (index in 0 until frames) {
            val white = random.nextFloat() * 2f - 1f
            previous = previous * lowPass + white * (1f - lowPass)
            body[index] = previous
        }
        // Cross-fade the tail into the head so looping never clicks.
        val fade = (SAMPLE_RATE / 4).coerceAtMost(frames / 4)
        for (index in 0 until fade) {
            val mix = index.toFloat() / fade
            body[index] = body[index] * mix + body[frames - fade + index] * (1f - mix)
        }
        val tremolo = 0.35f + 0.65f * sound.ordinal.coerceAtMost(3)
        val samples = ShortArray(frames)
        for (index in 0 until frames) {
            // Slow swell keeps the ambience from sounding machine-flat.
            val swell = 1f + 0.08f * sin(2 * PI * (index.toFloat() / frames) * tremolo).toFloat()
            samples[index] = (body[index] * gain * swell * Short.MAX_VALUE)
                .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
        }
        return samples
    }

    fun channelCount(): Int = CHANNELS
    fun bytesPerFrame(): Int = CHANNELS * 2
}

/**
 * Owns one looping [AudioTrack]. Volume changes reuse its buffer and never resume a
 * system-paused track. PCM generation happens outside the UI thread.
 */
object RainSoundPlayer {
    private const val TAG = "RainSound"
    private var track: AudioTrack? = null
    private var loadedSound: RainSound = RainSound.Off
    private var currentVolume = 0f
    private var pausedForSystem = false
    private val audioCoordinator = RuntimeAudioCoordinator(
        onPause = { pauseForSystem() },
        onResume = { resumeForSystem() },
    )

    @Synchronized
    internal fun play(context: Context, sound: RainSound, volume: Float, samples: ShortArray) {
        if (sound == RainSound.Off || volume <= 0.002f) {
            release()
            return
        }
        val appContext = context.applicationContext
        if (!isAudioPlaybackAllowed(appContext, readAppAudioSettings(appContext).background)) {
            release()
            return
        }
        val existing = track
        if (existing != null && loadedSound == sound) {
            applyVolume(existing, volume)
            return
        }
        release()
        runCatching {
            val bufferBytes = samples.size * RainNoise.bytesPerFrame()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            if (!audioCoordinator.request(appContext, attributes, AudioManager.AUDIOFOCUS_GAIN,
                    readAppAudioSettings(appContext).background)) return
            val created = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RainNoise.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(bufferBytes)
                .build()
            // Own the track before any operation that can fail, so cleanup cannot leak it.
            track = created
            check(created.write(samples, 0, samples.size) == samples.size) { "Incomplete rain audio buffer" }
            created.setLoopPoints(0, samples.size, -1)
            created.setVolume(volume)
            created.play()
            loadedSound = sound
            currentVolume = volume
            pausedForSystem = false
        }.onFailure {
            Log.e(TAG, "failed to start rain ambience", it)
            release()
        }
    }

    /** Adjust volume for an already running ambience without restarting it. */
    @Synchronized
    fun setVolume(volume: Float) {
        val existing = track ?: return
        applyVolume(existing, volume)
    }

    @Synchronized
    fun pauseForSystem() {
        val existing = track ?: return
        pausedForSystem = true
        runCatching { if (existing.playState == AudioTrack.PLAYSTATE_PLAYING) existing.pause() }
    }

    @Synchronized
    private fun resumeForSystem() {
        if (!pausedForSystem) return
        track?.let(::resume)
    }

    @Synchronized
    fun release() {
        audioCoordinator.release()
        track?.let { existing ->
            runCatching { existing.pause() }
            runCatching { existing.flush() }
            runCatching { existing.release() }
        }
        track = null
        loadedSound = RainSound.Off
        currentVolume = 0f
        pausedForSystem = false
    }

    internal fun isActive(): Boolean = track != null && !pausedForSystem

    private fun resume(existing: AudioTrack) {
        if (existing.playState != AudioTrack.PLAYSTATE_PLAYING) {
            runCatching { existing.play() }
        }
        pausedForSystem = false
    }

    private fun applyVolume(existing: AudioTrack, volume: Float) {
        val safe = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0f
        if (safe == currentVolume) return
        currentVolume = safe
        runCatching { existing.setVolume(safe) }
    }
}

/** Resume-only ownership prevents ambience surviving Home, another activity or screen lock. */
@Composable
internal fun RainAmbience(enabled: Boolean, sound: RainSound, volume: Float) {
    val context = LocalContext.current
    val latestVolume by rememberUpdatedState(volume)
    var resumed by remember { mutableStateOf(false) }
    var audioRules by remember(context) { mutableStateOf(readAppAudioSettings(context)) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose {
            resumed = false
            RainSoundPlayer.release()
        }
    }
    LaunchedEffect(enabled, resumed, sound, audioRules) {
        RainSoundPlayer.release()
        if (enabled && resumed && sound != RainSound.Off && isAudioPlaybackAllowed(context, audioRules.background)) {
            val samples = withContext(Dispatchers.Default) { RainNoise.render(sound) }
            RainSoundPlayer.play(context, sound, latestVolume, samples)
        } else RainSoundPlayer.release()
    }
    LaunchedEffect(volume) { RainSoundPlayer.setVolume(volume) }
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == CUSTOM_AUDIO_SETTINGS_KEY) audioRules = readAppAudioSettings(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            RainSoundPlayer.release()
        }
    }
}
