plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    implementation("org.json:json:20260814")
    testImplementation(kotlin("test"))
}
