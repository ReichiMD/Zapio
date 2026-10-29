plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// CI passes the run number, so every published build installs over the previous one.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Release signing comes only from CI secrets; the key never lives in this repo.
val releaseKeystore = System.getenv("ZAPIO_KEYSTORE")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "io.github.reichimd.zapio"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.reichimd.zapio"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        // CI adds "-test" for pre-releases built from pull requests.
        versionName = "0.0.$buildNumber" + (System.getenv("ZAPIO_VERSION_SUFFIX") ?: "")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("ZAPIO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ZAPIO_KEY_ALIAS")
                keyPassword = System.getenv("ZAPIO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":adb"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
