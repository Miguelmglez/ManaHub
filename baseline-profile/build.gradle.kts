/**
 * Baseline Profile generator module.
 *
 * This is a `com.android.test` module that runs on a connected device or emulator and
 * exercises critical user journeys, producing a `baseline-prof.txt` that is committed
 * to `app/src/main/` and consumed by the R8/ART profile installer at install time to
 * pre-compile hot code paths.
 *
 * --- HOW TO GENERATE ---
 * 1. Connect a device/emulator (API 28+).
 * 2. Run:  ./gradlew :baseline-profile:connectedAndroidTest
 * 3. Retrieve the generated profile from logcat or ADB and place it at:
 *        app/src/main/baseline-prof.txt
 *
 * NOTE: The `androidx.baselineprofile` Gradle plugin (which automates step 3) does not
 * yet support AGP 9.x (as of AGP 9.1.0). The `:generateBaselineProfile` task will be
 * wired when the plugin releases AGP 9 compatibility. Track progress at:
 * https://issuetracker.google.com/issues/336396420
 */
plugins {
    // android.test is already on the classpath via the root project — no version needed.
    // AGP 9+ has built-in Kotlin support; kotlin.android is NOT applied separately.
    id("com.android.test")
    // TODO: restore when androidx.baselineprofile supports AGP 9.x
    // id("androidx.baselineprofile") version "1.3.4"
}

android {
    namespace = "com.mmg.manahub.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.espresso.core)
    implementation(libs.baseline.profile.junit4)
    implementation(libs.uiautomator)
}
