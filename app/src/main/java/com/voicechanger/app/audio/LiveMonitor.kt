package com.voicechanger.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.voicechanger.app.engine.VoiceParams
import com.voicechanger.app.engine.VoiceProcessor

/**
 * Plays your converted voice back in real time so you can audition voices.
 * Use headphones, otherwise the speaker feeds back into the microphone.
 */
class LiveMonitor {
    @Volatile private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var pendingVoice: VoiceParams? = null

    val isRunning: Boolean get() = running

    fun setVoice(voice: VoiceParams) {
        pendingVoice = voice
    }

    @SuppressLint("MissingPermission")
    fun start(voice: VoiceParams) {
        if (running) return
        running = true
        pendingVoice = voice
        thread = Thread({ loop() }, "vc-monitor").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val rate = SAMPLE_RATE
        val frame = rate / 100 // 10 ms
        val recMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(recMin, frame * 4 * 2),
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(
                maxOf(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), frame * 4 * 2),
            )
            .build()
        val processor = VoiceProcessor(rate)
        val buf = ShortArray(frame)
        try {
            record.startRecording()
            track.play()
            while (running) {
                pendingVoice?.let {
                    processor.setVoice(it)
                    pendingVoice = null
                }
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                processor.process(buf, 0, n)
                track.write(buf, 0, n)
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            runCatching { track.stop() }
            track.release()
            processor.close()
            running = false
        }
    }

    companion object {
        const val SAMPLE_RATE = 48000
    }
}
