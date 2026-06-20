import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}


val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun requiredProperty(name: String): String =
    localProperties.getProperty(name)
        ?: System.getenv(name)
        ?: error("Missing required build property: $name. Add it to local.properties or set as environment variable in CI.")

android {
    namespace = "com.mmg.manahub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mmg.manahub"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0"

        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"${localProperties.getProperty("YOUTUBE_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${requiredProperty("SUPABASE_URL")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${requiredProperty("SUPABASE_ANON_KEY")}\""
        )
        buildConfigField(
            "String",
            "GOOGLE_CLIENT_ID",
            "\"${requiredProperty("GOOGLE_CLIENT_ID")}\""
        )
        buildConfigField(
            "String",
            "CLOUDFLARE_WORKER_URL",
            "\"${localProperties.getProperty("CLOUDFLARE_WORKER_URL", "https://manahub-draft-api.miguel-mglez.workers.dev/")}\""
        )

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
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

    signingConfigs {
        create("release") {
            val keystorePath = localProperties.getProperty("KEY_STORE_PATH", "")
            if (keystorePath.isNotEmpty()) {
                storeFile = file(keystorePath)
            }
            storePassword = localProperties.getProperty("KEY_STORE_PASSWORD", "")
            keyAlias = localProperties.getProperty("KEY_ALIAS", "")
            keyPassword = localProperties.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    androidResources {
        noCompress += listOf("tflite")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // Expose the exported Room schemas to instrumented tests so MigrationTestHelper
    // can load them from androidTest assets (required by Room 2.8 MigrationTestHelper).
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}



dependencies {
    // KMP migration — Phase 0, Spike A: shared pure-Kotlin domain models (CollectionViewMode, GroupingMode).
    implementation(project(":shared:core-model"))

    // Supabase Auth & DB
    val supabaseBom = platform(libs.supabase.bom)
    implementation(supabaseBom)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.android)

    // Google Sign-In
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)

    // Firebase
    implementation(libs.firebase.crashlytics)
    implementation(libs.splashscreen)
    implementation(libs.foundation)
    implementation(libs.material3)
    // tv.material removed — no usages found in the codebase (verified 2026-06-10)
    implementation(libs.emojis)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)

    // Koin — Phase 0 Spike D: runs ALONGSIDE Hilt to enable a per-feature Hilt→Koin cutover
    // (the Settings feature is the first "Koin island"; the rest stays on Hilt). Additive — no
    // Hilt dependency removed. koin-androidx-compose supplies koinViewModel() for Composables.
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)  // Kept for DraftModule (Cloudflare/YouTube manual JSON parsing)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.turbine)

    // Gson — needed directly by RoomConverters and CardEntityMapper
    implementation(libs.gson)

    // implementation("org.tensorflow:tensorflow-lite:2.16.1")
    // implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1")

    implementation(libs.accompanist.permissions)
    implementation(libs.material.icons.extended)
    implementation(libs.compose.googlefonts)
    implementation(libs.datastore.preferences)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.room.paging)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.browser)
    implementation(libs.guava)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.youtube.player)

    // Nearby Connections — peer-to-peer in-person game state sync
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    implementation(libs.play.app.update)
    implementation(libs.play.review)

    // Vosk — offline grammar-restricted voice recognition
    implementation(libs.vosk.android)

    // Stable collection types for Compose recomposition stability
    implementation(libs.kotlinx.collections.immutable)
}