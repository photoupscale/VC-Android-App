// Compile-only stubs of the Xposed API. LSPosed provides the real classes at
// runtime inside the hooked app, so nothing from here is packaged in the APK.
plugins {
    id("com.android.library")
}

android {
    namespace = "de.robv.android.xposed.stubs"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
