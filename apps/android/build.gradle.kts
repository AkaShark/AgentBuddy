plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.github.triplet.play") version "3.12.1" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}

// ABIs to build and package. CI sets ANDROID_ABIS; local builds use the ABIs
// tools/scripts/build-android-rust.sh last put in core/bridge jniLibs, so
// Android Studio and `make` package the same set without extra setup
// (`make android-release` relies on this too: it does not export ANDROID_ABIS).
// The configuration cache tracks these file checks, so it refreshes when the
// set of built ABIs changes.
val defaultAndroidAbis = listOf("arm64-v8a", "x86_64")
val bridgeJniLibs = file("core/bridge/src/main/jniLibs")
extra["androidAbis"] = System.getenv("ANDROID_ABIS")
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() }
    ?.takeIf { it.isNotEmpty() }
    ?: defaultAndroidAbis
        .filter { File(bridgeJniLibs, "$it/libcodex_mobile_client.so").isFile }
        .ifEmpty { defaultAndroidAbis }
