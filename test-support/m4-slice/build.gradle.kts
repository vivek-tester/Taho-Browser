plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":transfer:core"))
    implementation(project(":capture:domain"))
    implementation(project(":contract:taho-transfer"))
    implementation(project(":test-support:http-fixtures"))
    implementation(project(":test-support:taho-receiver-harness"))
    testImplementation(kotlin("test"))
}
