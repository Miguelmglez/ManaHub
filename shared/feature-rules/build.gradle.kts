plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.mmg.manahub.feature.rules.resources"
}

kotlin {
    androidLibrary {
        namespace = "com.mmg.manahub.feature.rules"
        compileSdk = 37
        minSdk = 29
        withHostTestBuilder {}
    }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-model"))
            implementation(project(":shared:core-domain"))
            implementation(project(":shared:core-common"))
            implementation(project(":shared:core-ui"))
            implementation(libs.coroutines.core)
            implementation(libs.koin.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}
