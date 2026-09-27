plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":contract:taho-transfer"))
    implementation(project(":capture:domain"))
    testImplementation(kotlin("test"))
}
