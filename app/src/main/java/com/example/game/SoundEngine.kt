package com.example.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SoundEngine {
    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var sfxVolume = 0.85f

    @Volatile
    private var buzzVolume = 0.70f

    @Volatile
    private var initialized = false

    private var ambientJob: Job? = null
    private var ambientTrack: AudioTrack? = null

    @Volatile
    private var activeBeeCount = 0

    @Volatile
    private var closestBeeDist = 300f

    @Volatile
    private var closestLavaDist = 999f

    @Volatile
    private var ambientActive = false

    private var lastScribbleMs = 0L
    private var lastBounceMs = 0L
    private var lastLavaBubbleMs = 0L

    fun init() {
        initialized = true
    }

    fun updateVolumes(sfx: Float, buzz: Float) {
        sfxVolume = sfx.coerceIn(0f, 1f)
        buzzVolume = buzz.coerceIn(0f, 1f)
        if (buzzVolume <= 0.01f && sfxVolume <= 0.01f) {
            runCatching { ambientTrack?.setVolume(0f) }
        } else {
            runCatching { ambientTrack?.setVolume(1f) }
        }
    }

    fun playScribble(speedPxPerSec: Float) {
        if (!initialized || sfxVolume <= 0.01f) return
        val now = System.currentTimeMillis()
        if (now - lastScribbleMs < 55L) return
        lastScribbleMs = now

        val normSpeed = (speedPxPerSec / 1200f).coerceIn(0f, 1f)
        val freq = 420f + normSpeed * 480f
        val samples = synthesizeBuffer(0.045f) { t, progress ->
            val env = (1f - progress).pow(1.6f)
            val noise = (((t * 17300f).toInt() % 100) / 50f - 1f) * 0.22f
            (sin(2.0 * PI * freq * t).toFloat() * 0.45f + noise) * env * 0.25f * sfxVolume
        }
        playOneShot(samples)
    }

    fun playBounce(isSpring: Boolean = false) {
        if (!initialized || sfxVolume <= 0.01f) return
        val now = System.currentTimeMillis()
        if (!isSpring && now - lastBounceMs < 65L) return
        lastBounceMs = now

        val startFreq = if (isSpring) 280f else 480f
        val endFreq = if (isSpring) 760f else 820f
        val dur = if (isSpring) 0.16f else 0.085f
        val samples = synthesizeBuffer(dur) { _, progress ->
            val freq = startFreq + (endFreq - startFreq) * progress
            val env = (1f - progress).pow(1.4f)
            sin(2.0 * PI * freq * progress * dur).toFloat() * env * 0.42f * sfxVolume
        }
        playOneShot(samples)
    }

    fun playSkinSelect(skin: HeroSkin) {
        if (!initialized || sfxVolume <= 0.01f) return
        val pcm = when (skin) {
            HeroSkin.DOGE -> synthesizeDogeSelect()
            HeroSkin.NEON_CAT -> synthesizeNeonCatSelect()
            HeroSkin.ESPRESSO_CUP -> synthesizeEspressoSelect()
            HeroSkin.CROWN_FROG -> synthesizeCrownFrogSelect()
        }
        playOneShot(pcm)
    }

    fun playLockedSkin() {
        if (!initialized || sfxVolume <= 0.01f) return
        val pcm = synthesizeBuffer(0.14f) { t, _ ->
            val tap1 = if (t < 0.06f) {
                val p = t / 0.06f
                val env = exp(-p * 14f)
                sin(2.0 * PI * (210f - p * 60f) * t).toFloat() * env
            } else 0f
            val tap2 = if (t in 0.07f..0.14f) {
                val lt = t - 0.07f
                val p = lt / 0.07f
                val env = exp(-p * 16f)
                sin(2.0 * PI * (170f - p * 55f) * lt).toFloat() * env
            } else 0f
            (tap1 + tap2) * 0.34f * sfxVolume
        }
        playOneShot(pcm)
    }

    fun maybePlayLavaBubble(closestLavaDist: Float) {
        if (!initialized || sfxVolume <= 0.01f || closestLavaDist > 185f) return
        val proximity = ((185f - closestLavaDist) / 145f).coerceIn(0f, 1f)
        val cooldownMs = (950L - (proximity * 610f).toLong()).coerceAtLeast(340L)
        val now = System.currentTimeMillis()
        if (now - lastLavaBubbleMs < cooldownMs) return
        lastLavaBubbleMs = now

        val pitchOffset = ((now % 5) - 2) * 24f
        val dur = 0.085f
        var phase = 0.0
        var seed = (now and 0x7FFF).toInt().coerceAtLeast(137)
        val samples = synthesizeBuffer(dur) { _, progress ->
            val freq = (185f + pitchOffset) + (430f * progress.pow(1.5f))
            phase += 2.0 * PI * freq / sampleRate
            val env = sin(PI * progress).toFloat().pow(0.75f) * (1f - progress * 0.35f)
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            val noise = ((seed shr 16) / 16384f - 1f)
            val pop = if (progress > 0.72f) noise * ((progress - 0.72f) / 0.28f) * 0.35f else 0f
            val bloop = sin(phase).toFloat() * 0.75f
            (bloop + pop) * env * (0.18f + 0.24f * proximity) * sfxVolume
        }
        playOneShot(samples)
    }

    fun playTickTock(secondsLeft: Int) {
        if (!initialized || sfxVolume <= 0.01f) return
        val freq = if (secondsLeft == 1) 1040f else 840f
        val samples = synthesizeBuffer(0.08f) { t, progress ->
            val env = exp(-progress * 8f)
            sin(2.0 * PI * freq * t).toFloat() * env * 0.48f * sfxVolume
        }
        playOneShot(samples)
    }

    fun playVictory() {
        if (!initialized || sfxVolume <= 0.01f) return
        val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
        val noteDur = 0.11f
        val totalSamples = (sampleRate * (notes.size * noteDur + 0.18f)).toInt()
        val pcm = ShortArray(totalSamples)
        notes.forEachIndexed { idx, freq ->
            val startSample = (idx * noteDur * sampleRate).toInt()
            val len = ((noteDur + 0.14f) * sampleRate).toInt()
            for (i in 0 until len) {
                val outIdx = startSample + i
                if (outIdx >= totalSamples) break
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / len
                val env = (1f - progress).pow(1.2f)
                val tone = sin(2.0 * PI * freq * t).toFloat() + 0.25f * sin(4.0 * PI * freq * t).toFloat()
                val mixed = (pcm[outIdx] / 32767f) + tone * env * 0.35f * sfxVolume
                pcm[outIdx] = (mixed.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
        }
        playOneShot(pcm)
    }

    fun playFail(reason: FailReason = FailReason.NONE) {
        if (!initialized || sfxVolume <= 0.01f) return
        if (reason == FailReason.LAVA_BURN) {
            var seed = 9187
            var prevNoise = 0f
            var phase = 0.0
            val dur = 0.56f
            val samples = synthesizeBuffer(dur) { _, progress ->
                val freq = 320f - progress * 210f
                phase += 2.0 * PI * freq / sampleRate
                seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
                val white = ((seed shr 16) / 16384f - 1f)
                val hiss = (white - prevNoise * 0.72f)
                prevNoise = white
                val hissEnv = exp(-progress * 3.8f) * (0.45f + 0.55f * sin(progress * PI * 6f).toFloat().coerceAtLeast(0f))
                val dropEnv = (1f - progress).pow(1.1f)
                val wave = sin(phase).toFloat() * 0.45f * dropEnv + hiss * 0.42f * hissEnv
                wave * 0.48f * sfxVolume
            }
            playOneShot(samples)
            return
        }

        val dur = 0.45f
        val samples = synthesizeBuffer(dur) { t, progress ->
            val freq = 360f - progress * 220f + sin(t * 28.0).toFloat() * 16f
            val env = (1f - progress).pow(0.9f)
            val wave = sin(2.0 * PI * freq * t).toFloat() + 0.3f * sin(4.0 * PI * freq * t).toFloat()
            wave * env * 0.36f * sfxVolume
        }
        playOneShot(samples)
    }

    fun startBeeBuzz() {
        startAmbientLoop()
    }

    fun startAmbientLoop() {
        if (!initialized || ambientActive) return
        ambientActive = true
        ambientJob?.cancel()
        ambientJob = scope.launch {
            runCatching {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                ambientTrack = track
                track.play()

                val chunk = ShortArray(512)
                var beePhase = 0.0
                var beeLfoPhase = 0.0
                var lavaPhaseA = 0.0
                var lavaPhaseB = 0.0
                var lavaLfoPhase = 0.0
                var noiseSeed = 424242
                var prevWhite = 0f

                while (isActive && ambientActive) {
                    val count = activeBeeCount
                    val bVol = buzzVolume
                    val sVol = sfxVolume

                    val beeActive = count > 0 && bVol > 0.01f
                    val lavaProx = ((230f - closestLavaDist) / 180f).coerceIn(0f, 1f)
                    val lavaActive = lavaProx > 0.02f && sVol > 0.01f

                    if (!beeActive && !lavaActive) {
                        chunk.fill(0)
                        track.write(chunk, 0, chunk.size)
                        continue
                    }

                    val beeProx = ((240f - closestBeeDist) / 200f).coerceIn(0f, 1f)
                    val beeFreq = 155.0 + beeProx * 95.0
                    val beeAmp = if (beeActive) (0.06f + beeProx * 0.16f) * bVol else 0f

                    val rumbleAmp = if (lavaActive) (0.045f + 0.14f * lavaProx * lavaProx) * sVol else 0f
                    val sizzleAmp = if (lavaActive) (0.015f + 0.085f * lavaProx.pow(1.4f)) * sVol else 0f

                    for (i in chunk.indices) {
                        var mixed = 0f

                        if (beeActive) {
                            beeLfoPhase += 2.0 * PI * 18.0 / sampleRate
                            if (beeLfoPhase > 2.0 * PI) beeLfoPhase -= 2.0 * PI
                            val mod = 1.0 + 0.08 * sin(beeLfoPhase)
                            beePhase += 2.0 * PI * (beeFreq * mod) / sampleRate
                            if (beePhase > 2.0 * PI) beePhase -= 2.0 * PI
                            val saw = (beePhase / PI - 1.0).toFloat()
                            mixed += (saw * 0.5f + sin(beePhase * 2.0).toFloat() * 0.35f) * beeAmp
                        }

                        if (lavaActive) {
                            lavaLfoPhase += 2.0 * PI * 3.4 / sampleRate
                            if (lavaLfoPhase > 2.0 * PI) lavaLfoPhase -= 2.0 * PI
                            val swell = (0.78 + 0.22 * sin(lavaLfoPhase)).toFloat()

                            lavaPhaseA += 2.0 * PI * (52.0 + 12.0 * lavaProx) / sampleRate
                            lavaPhaseB += 2.0 * PI * (81.0 + 18.0 * lavaProx) / sampleRate
                            if (lavaPhaseA > 2.0 * PI) lavaPhaseA -= 2.0 * PI
                            if (lavaPhaseB > 2.0 * PI) lavaPhaseB -= 2.0 * PI

                            val rumble = (sin(lavaPhaseA).toFloat() * 0.65f + sin(lavaPhaseB).toFloat() * 0.35f) * rumbleAmp * swell

                            noiseSeed = (noiseSeed * 1103515245 + 12345) and 0x7FFFFFFF
                            val white = ((noiseSeed shr 16) / 16384f - 1f)
                            val hpNoise = white - prevWhite * 0.85f
                            prevWhite = white
                            val crackleGate = if (((noiseSeed ushr 8) and 0x1F) == 0) 2.2f else 0.7f
                            val sizzle = hpNoise * crackleGate * sizzleAmp

                            mixed += rumble + sizzle
                        }

                        chunk[i] = (mixed.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    track.write(chunk, 0, chunk.size)
                }
                track.stop()
                track.release()
                ambientTrack = null
            }
        }
    }

    fun updateBeeBuzz(beeCount: Int, closestDist: Float) {
        activeBeeCount = beeCount
        closestBeeDist = closestDist
    }

    fun updateEnvironmentAudio(beeCount: Int, closestBeeDist: Float, closestLavaDist: Float) {
        activeBeeCount = beeCount
        this.closestBeeDist = closestBeeDist
        this.closestLavaDist = closestLavaDist
    }

    fun stopBeeBuzz() {
        stopAmbientLoop()
    }

    fun stopAmbientLoop() {
        ambientActive = false
        activeBeeCount = 0
        closestLavaDist = 999f
        ambientJob?.cancel()
        ambientJob = null
        runCatching {
            ambientTrack?.pause()
            ambientTrack?.flush()
            ambientTrack?.release()
        }
        ambientTrack = null
    }

    fun release() {
        stopAmbientLoop()
        scope.cancel()
    }

    private fun synthesizeDogeSelect(): ShortArray {
        val dur = 0.24f
        var phase = 0.0
        return synthesizeBuffer(dur) { t, _ ->
            val yip1 = if (t < 0.09f) {
                val p = t / 0.09f
                val f = 430f + sin(p * PI).toFloat() * 360f
                phase += 2.0 * PI * f / sampleRate
                val env = sin(p * PI).toFloat().pow(0.7f)
                (sin(phase).toFloat() + 0.35f * sin(phase * 2.0).toFloat()) * env
            } else 0f

            val yip2 = if (t in 0.11f..0.22f) {
                val p = (t - 0.11f) / 0.11f
                val f = 510f + sin(p * PI).toFloat() * 410f
                phase += 2.0 * PI * f / sampleRate
                val env = sin(p * PI).toFloat().pow(0.65f)
                (sin(phase).toFloat() + 0.35f * sin(phase * 2.0).toFloat()) * env
            } else 0f

            (yip1 * 0.78f + yip2) * 0.34f * sfxVolume
        }
    }

    private fun synthesizeNeonCatSelect(): ShortArray {
        val dur = 0.28f
        var phase = 0.0
        var shimmerPhase = 0.0
        return synthesizeBuffer(dur) { _, progress ->
            val f = 680f + 620f * sin(progress * PI * 0.85f).toFloat()
            phase += 2.0 * PI * f / sampleRate
            shimmerPhase += 2.0 * PI * (1568f + 525f * progress) / sampleRate
            val env = sin(progress * PI).toFloat().pow(0.55f) * (1f - progress * 0.3f)
            val fm = sin(phase + 0.65 * sin(phase * 2.0)).toFloat()
            val sparkle = sin(shimmerPhase).toFloat() * 0.35f * progress
            (fm * 0.7f + sparkle) * env * 0.34f * sfxVolume
        }
    }

    private fun synthesizeEspressoSelect(): ShortArray {
        val dur = 0.26f
        var seed = 31337
        var prevNoise = 0f
        return synthesizeBuffer(dur) { t, _ ->
            val clinkEnv = exp(-t * 24f)
            val clink = (
                sin(2.0 * PI * 1480.0 * t).toFloat() * 0.50f +
                    sin(2.0 * PI * 2360.0 * t).toFloat() * 0.35f +
                    sin(2.0 * PI * 3280.0 * t).toFloat() * 0.25f
                ) * clinkEnv

            val steam = if (t > 0.07f) {
                val sp = (t - 0.07f) / (dur - 0.07f)
                seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
                val white = ((seed shr 16) / 16384f - 1f)
                val hp = white - prevNoise * 0.78f
                prevNoise = white
                hp * sin(sp * PI).toFloat().pow(0.8f) * 0.38f
            } else 0f

            (clink * 0.72f + steam) * 0.42f * sfxVolume
        }
    }

    private fun synthesizeCrownFrogSelect(): ShortArray {
        val dur = 0.30f
        var phase = 0.0
        return synthesizeBuffer(dur) { t, _ ->
            val pulse1 = if (t < 0.09f) {
                val p = t / 0.09f
                val f = 140f + 165f * p
                phase += 2.0 * PI * f / sampleRate
                val rib = (0.5f + 0.5f * sin(2.0 * PI * 40.0 * t).toFloat())
                sin(phase + 0.9 * sin(phase * 2.0)).toFloat() * rib * sin(p * PI).toFloat()
            } else 0f

            val pulse2 = if (t in 0.11f..0.21f) {
                val lt = t - 0.11f
                val p = lt / 0.10f
                val f = 175f + 195f * p
                phase += 2.0 * PI * f / sampleRate
                val rib = (0.5f + 0.5f * sin(2.0 * PI * 44.0 * lt).toFloat())
                sin(phase + 0.9 * sin(phase * 2.0)).toFloat() * rib * sin(p * PI).toFloat()
            } else 0f

            val crownPing = if (t >= 0.17f) {
                val lt = t - 0.17f
                val p = lt / (dur - 0.17f)
                val env = exp(-p * 6f)
                (sin(2.0 * PI * 1046.5 * lt).toFloat() + 0.5f * sin(2.0 * PI * 1567.98 * lt).toFloat()) * env * 0.45f
            } else 0f

            (pulse1 * 0.8f + pulse2 * 0.9f + crownPing) * 0.36f * sfxVolume
        }
    }

    private inline fun synthesizeBuffer(
        durationSec: Float,
        generator: (t: Float, progress: Float) -> Float
    ): ShortArray {
        val count = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val p = i.toFloat() / count
            out[i] = (generator(t, p).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun playOneShot(pcm: ShortArray) {
        scope.launch {
            runCatching {
                val byteCount = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                val sleepMs = (pcm.size * 1000L / sampleRate) + 40L
                kotlinx.coroutines.delay(sleepMs)
                track.stop()
                track.release()
            }
        }
    }
}
