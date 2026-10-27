package com.voicechanger.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Mono 16-bit PCM decoded from an audio or video file. */
class DecodedAudio(val pcm: ShortArray, val length: Int, val sampleRate: Int)

/** Decodes the first audio track of any format Android supports (m4a, mp3, WhatsApp opus, wav, mp4...). */
object AudioDecoder {
    private const val TIMEOUT_US = 10_000L

    fun decodeToMono(context: Context, uri: Uri, maxSeconds: Int = 60): DecodedAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This file has no audio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return drain(extractor, codec, format, maxSeconds)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, input: MediaFormat, maxSeconds: Int): DecodedAudio {
        var sampleRate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        var out = ShortArray(sampleRate * 10)
        var length = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(TIMEOUT_US)
                if (i >= 0) {
                    val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val f = codec.outputFormat
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            } else if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val frames = info.size / (channels * if (floatPcm) 4 else 2)
                if (length + frames > out.size) out = out.copyOf(maxOf(out.size * 2, length + frames))
                for (k in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until channels) sum += if (floatPcm) buf.float * 32767f else buf.short.toFloat()
                    out[length++] = (sum / channels).coerceIn(-32768f, 32767f).toInt().toShort()
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || length >= sampleRate * maxSeconds) break
            }
        }
        return DecodedAudio(out, minOf(length, sampleRate * maxSeconds), sampleRate)
    }
}
