package com.voicechanger.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.voicechanger.app.engine.VoiceParams
import com.voicechanger.app.engine.VoiceProcessor
import java.io.File

/**
 * No-root mode: records the microphone, converts it while recording, and
 * saves an AAC .m4a that can be shared to WhatsApp or Messenger as audio.
 */
class VoiceNoteRecorder {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var pcm = ShortArray(0)
    @Volatile private var length = 0

    val isRecording: Boolean get() = running

    /** Seconds recorded so far. */
    val seconds: Float get() = length.toFloat() / SAMPLE_RATE

    @SuppressLint("MissingPermission")
    fun start(voice: VoiceParams) {
        if (running) return
        running = true
        pcm = ShortArray(SAMPLE_RATE * 60)
        length = 0
        thread = Thread({ record(voice) }, "vc-note").apply { start() }
    }

    /** Stops recording and writes the converted note to [out]. Returns false if nothing was recorded. */
    fun stopAndSave(out: File): Boolean {
        running = false
        thread?.join()
        thread = null
        if (length < SAMPLE_RATE / 4) return false
        out.parentFile?.mkdirs()
        AacWriter.write(out, pcm, length, SAMPLE_RATE)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun record(voice: VoiceParams) {
        val frame = SAMPLE_RATE / 50
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame * 8),
        )
        val processor = VoiceProcessor(SAMPLE_RATE).apply { setVoice(voice) }
        val buf = ShortArray(frame)
        try {
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                processor.process(buf, 0, n)
                if (length + n > pcm.size) pcm = pcm.copyOf(pcm.size * 2)
                System.arraycopy(buf, 0, pcm, length, n)
                length += n
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            processor.close()
        }
    }

    companion object {
        const val SAMPLE_RATE = 48000
    }
}
