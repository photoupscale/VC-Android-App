package com.voicechanger.app.hook

/** The call hook replaces this in our own process, so the app can show whether LSPosed loaded it. */
object ModuleStatus {
    @JvmStatic
    fun isActive(): Boolean = false
}
