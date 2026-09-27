plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

val pureJvmModules = listOf(
    "capture/domain",
    "contract/taho-transfer",
    "transfer/core",
)

tasks.register("verifyArchitecture") {
    group = "verification"
    description = "Fail if correctness-critical pure JVM modules gain Android dependencies."

    doLast {
        pureJvmModules.forEach { module ->
            val moduleBuild = file("$module/build.gradle.kts").readText()
            if ("com.android." in moduleBuild) {
                throw GradleException("$module must remain a pure JVM module")
            }

            fileTree(module) {
                include("src/**/*.kt")
            }.forEach { source ->
                val sourceText = source.readText()
                val androidImport = Regex(
                    "^\\s*import\\s+android\\.",
                    RegexOption.MULTILINE,
                )
                if (androidImport.containsMatchIn(sourceText)) {
                    throw GradleException(
                        "$module imports android.* from ${source.relativeTo(projectDir)}",
                    )
                }
            }
        }
    }
}
