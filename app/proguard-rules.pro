# LSPosed loads the hook by the class name in assets/xposed_init and hooks
# ModuleStatus.isActive() by name, so neither may be renamed or inlined.
-keep class com.voicechanger.app.hook.** { *; }

# JNI functions are bound by class and method name.
-keep class com.voicechanger.app.engine.NativeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Provided by LSPosed at runtime; only compile-time stubs exist in this build.
-dontwarn de.robv.android.xposed.**
