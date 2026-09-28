plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val geckoViewVersion: String by project

android {
    namespace = "app.taho.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.taho.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "M1_ATTRIBUTION_VERIFIED", "true")
        buildConfigField("String", "GECKOVIEW_VERSION", "\"$geckoViewVersion\"")
        buildConfigField("String", "TAHO_PACKAGE_NAME", "\"com.eternal.taho\"")
        buildConfigField("String", "TAHO_TRANSFER_ACTION", "\"com.eternal.taho.action.IMPORT_TAHO_REQUEST\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: "taho_browser_release"
            val keyAlias = System.getenv("KEY_ALIAS") ?: "taho"
            val keyPassword = System.getenv("KEY_PASSWORD") ?: "taho_browser_release"
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":browser:shell"))
    implementation(project(":browser:runtime"))
    implementation(project(":browser:observation"))
    implementation(project(":capture:domain"))
    implementation(project(":capture:persist"))
    implementation(project(":transfer:core"))
    implementation(project(":transfer:android"))
    implementation(project(":contract:taho-transfer"))
    implementation("org.mozilla.geckoview:geckoview-omni:$geckoViewVersion")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui:1.9.1")
    implementation("androidx.compose.foundation:foundation:1.9.1")
    implementation("androidx.compose.foundation:foundation-layout:1.9.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.biometric:biometric:1.1.0")
}
