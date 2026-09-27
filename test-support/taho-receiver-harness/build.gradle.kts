plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":contract:taho-transfer"))
    testImplementation(kotlin("test"))
}
