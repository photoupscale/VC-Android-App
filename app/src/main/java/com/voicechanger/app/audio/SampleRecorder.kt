package com.voicechanger.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Records the unprocessed microphone for voice analysis. */
class SampleRecorder {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val pcm = ShortArray(SAMPLE_RATE * MAX_SECONDS)
    @Volatile private var length = 0

    val isRecording: Boolean get() = running
    val seconds: Float get() = length.toFloat() / SAMPLE_RATE

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        length = 0
        thread = Thread(::record, "vc-sample").apply { start() }
    }

    fun stop(): DecodedAudio {
        running = false
        thread?.join()
        thread = null
        return DecodedAudio(pcm, length, SAMPLE_RATE)
    }

    @SuppressLint("MissingPermission")
    private fun record() {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, SAMPLE_RATE / 5),
        )
        try {
            rec.startRecording()
            while (running && length < pcm.size) {
                val n = rec.read(pcm, length, minOf(SAMPLE_RATE / 50, pcm.size - length))
                if (n > 0) length += n
            }
        } finally {
            running = false
            runCatching { rec.stop() }
            rec.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_SECONDS = 30
    }
}
