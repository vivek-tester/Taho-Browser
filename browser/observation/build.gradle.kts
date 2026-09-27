plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.taho.browser.observation"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val geckoViewVersion: String by project

dependencies {
    implementation(project(":capture:domain"))
    implementation("org.mozilla.geckoview:geckoview-omni:$geckoViewVersion")
}
