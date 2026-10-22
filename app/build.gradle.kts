plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// CI sets these; local builds get versionCode 1 so any CI build counts as newer.
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val githubRepo = System.getenv("GITHUB_REPOSITORY") ?: "hystericmoon/internet-speed-meter"

android {
    namespace = "com.example.speedmeter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.speedmeter"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRunNumber ?: 1
        versionName = "0.1.${ciRunNumber ?: 0}"

        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://github.com/$githubRepo/releases/download/debug-latest/version.json\"",
        )
    }

    signingConfigs {
        // Committed debug keystore so every CI build has the same signature and
        // installs over the previous one on the phone. Debug-only, not a secret.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        // Robolectric needs merged resources (fonts, strings) for the screenshot tests.
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

roborazzi {
    // Baselines are committed; diffs on failure land in build/outputs/roborazzi.
    outputDir.set(file("src/test/screenshots"))
}
