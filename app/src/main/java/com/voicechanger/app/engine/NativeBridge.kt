package com.voicechanger.app.engine

import java.nio.ByteBuffer

/** JNI entry points into the C++ voice engine (libvoiceengine.so). */
object NativeBridge {
    @Volatile
    var isLoaded = false
        private set

    /** Normal load path inside this app's own process. */
    @Synchronized
    fun loadDefault() {
        if (isLoaded) return
        System.loadLibrary("voiceengine")
        isLoaded = true
    }

    /**
     * Load from an absolute path. Used inside WhatsApp/Messenger, where our
     * library is not on the host app's library path. Must live in this class so
     * the library is bound to the module class loader that declares the natives.
     */
    @Synchronized
    fun loadFrom(path: String) {
        if (isLoaded) return
        System.load(path)
        isLoaded = true
    }

    @JvmStatic external fun nativeCreate(sampleRate: Int): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeSetParams(handle: Long, pitch: Float, formant: Float, brightness: Float, breathiness: Float, mix: Float)
    @JvmStatic external fun nativeSetEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeLatencyMs(handle: Long): Int
    @JvmStatic external fun nativeProcessShorts(handle: Long, data: ShortArray, offset: Int, length: Int, channels: Int)
    @JvmStatic external fun nativeProcessFloats(handle: Long, data: FloatArray, offset: Int, length: Int, channels: Int)
    @JvmStatic external fun nativeProcessBytes(handle: Long, data: ByteArray, offset: Int, lengthBytes: Int, channels: Int, encoding: Int)
    @JvmStatic external fun nativeProcessDirect(handle: Long, data: ByteBuffer, offsetBytes: Int, lengthBytes: Int, channels: Int, encoding: Int)
}
