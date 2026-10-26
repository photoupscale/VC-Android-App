package com.voicechanger.app.engine

import java.io.Closeable
import java.nio.ByteBuffer

/** One streaming converter instance. Not thread-safe; use from one audio thread. */
class VoiceProcessor(val sampleRate: Int) : Closeable {
    private var handle: Long = NativeBridge.nativeCreate(sampleRate)

    /** Settings revision last applied, so callers can skip redundant updates. */
    var appliedRevision: Long = -1

    val isClosed: Boolean get() = handle == 0L

    val latencyMs: Int get() = NativeBridge.nativeLatencyMs(handle)

    fun setVoice(voice: VoiceParams) {
        NativeBridge.nativeSetParams(handle, voice.pitchSemitones, voice.formantRatio, voice.brightnessDb, voice.breathiness, voice.mix)
    }

    fun setEnabled(enabled: Boolean) = NativeBridge.nativeSetEnabled(handle, enabled)

    fun process(data: ShortArray, offset: Int, length: Int, channels: Int = 1) =
        NativeBridge.nativeProcessShorts(handle, data, offset, length, channels)

    fun process(data: FloatArray, offset: Int, length: Int, channels: Int = 1) =
        NativeBridge.nativeProcessFloats(handle, data, offset, length, channels)

    fun process(data: ByteArray, offset: Int, lengthBytes: Int, channels: Int, encoding: Int) =
        NativeBridge.nativeProcessBytes(handle, data, offset, lengthBytes, channels, encoding)

    fun process(data: ByteBuffer, offsetBytes: Int, lengthBytes: Int, channels: Int, encoding: Int) =
        NativeBridge.nativeProcessDirect(handle, data, offsetBytes, lengthBytes, channels, encoding)

    override fun close() {
        if (handle != 0L) {
            NativeBridge.nativeDestroy(handle)
            handle = 0L
        }
    }
}
