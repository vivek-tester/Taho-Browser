plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val geckoViewVersion: String by project

android {
    namespace = "app.taho.browser.spike"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.taho.browser.spike"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "GECKOVIEW_VERSION",
            "\"$geckoViewVersion\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":browser:runtime"))
    implementation(project(":browser:observation"))
    testImplementation(kotlin("test"))
}
