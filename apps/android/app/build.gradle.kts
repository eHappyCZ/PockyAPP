plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.pokecio.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pokecio.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 913
        versionName = "0.10.0 - BETA"

        ndk {
            abiFilters += providers.environmentVariable("POCKY_ABIS")
                .orElse("arm64-v8a").get().split(" ").filter { it.isNotBlank() }
        }
    }

    val keystore = providers.environmentVariable("POCKY_KEYSTORE").orNull

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("POCKY_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("POCKY_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("POCKY_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-bindings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.emoji.picker)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.maplibre)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
