plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.taho.browser.capture.persist"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":capture:domain"))
    implementation("androidx.room:room-runtime:2.8.5")
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.10.4")
    implementation("androidx.core:core-ktx:1.17.0")

    testImplementation(kotlin("test"))
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
