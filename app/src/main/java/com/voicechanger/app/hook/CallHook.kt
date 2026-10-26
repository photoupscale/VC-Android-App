package com.voicechanger.app.hook

import android.media.AudioRecord
import com.voicechanger.app.BuildConfig
import com.voicechanger.app.engine.VoiceProcessor
import com.voicechanger.app.settings.TargetApp
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.nio.ByteBuffer

/**
 * LSPosed entry point (see assets/xposed_init).
 *
 * WhatsApp and Messenger capture the microphone through android.media.AudioRecord
 * (WebRTC's Java audio device). We hook its read() overloads inside those apps
 * and convert each buffer in place right after the platform fills it, before
 * the app encodes and sends it. Nothing is routed through speakers or loopback.
 */
class CallHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            XposedHelpers.findAndHookMethod(ModuleStatus::class.java.name, lpparam.classLoader, "isActive",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.setResult(true)
                    }
                })
            return
        }
        if (lpparam.packageName !in TargetApp.packageNames) return
        if (session != null) return

        val s = MicSession(lpparam.packageName, modulePath)
        session = s
        XposedBridge.log("VoiceChanger: hooking AudioRecord in ${lpparam.packageName}")

        XposedBridge.hookAllMethods(AudioRecord::class.java, "read", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                guarded { onRead(s, param) }
            }
        })
        XposedBridge.hookAllMethods(AudioRecord::class.java, "startRecording", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                guarded { (param.thisObject as? AudioRecord)?.let(s::onStart) }
            }
        })
        XposedBridge.hookAllMethods(AudioRecord::class.java, "release", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                guarded { (param.thisObject as? AudioRecord)?.let(s::onRelease) }
            }
        })
    }

    private fun onRead(s: MicSession, param: XC_MethodHook.MethodHookParam) {
        val record = param.thisObject as? AudioRecord ?: return
        val count = param.getResult() as? Int ?: return
        if (count <= 0) return
        val processor = s.processorFor(record) ?: return
        val channels = record.channelCount
        val encoding = record.audioFormat
        // release() may run on another thread; it closes the processor under the same lock.
        synchronized(processor) {
            if (processor.isClosed) return
            convertBuffer(processor, param.args, count, channels, encoding)
        }
    }

    private fun convertBuffer(processor: VoiceProcessor, args: Array<Any?>, count: Int, channels: Int, encoding: Int) {
        when (val buffer = args[0]) {
            is ShortArray -> processor.process(buffer, args[1] as Int, count, channels)
            is FloatArray -> processor.process(buffer, args[1] as Int, count, channels)
            is ByteArray -> processor.process(buffer, args[1] as Int, count, channels, encoding)
            // read(ByteBuffer, size) writes at position() and leaves position unchanged.
            is ByteBuffer -> if (buffer.isDirect) {
                processor.process(buffer, buffer.position(), count, channels, encoding)
            }
        }
    }

    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            // Never let a bug here crash the user's call.
            session?.disable(t)
        }
    }

    companion object {
        @Volatile private var modulePath: String? = null
        @Volatile private var session: MicSession? = null
    }
}
