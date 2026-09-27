plugins {
    id("com.android.application") version "9.1.1" apply false
    id("com.android.library") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

val pureJvmModules = listOf(
    "capture/domain",
    "contract/taho-transfer",
    "transfer/core",
    "test-support/http-fixtures",
    "test-support/taho-receiver-harness",
    "test-support/m4-slice",
)

val captureReachableModules = listOf(
    "capture/domain",
    "capture/persist",
    "browser/observation",
    "transfer/core",
    "transfer/android",
    "contract/taho-transfer",
)

tasks.register("verifyArchitecture") {
    group = "verification"
    description = "Fail if correctness/security-critical module boundaries are violated."

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

        captureReachableModules.forEach { module ->
            fileTree(module) {
                include("src/**/*.kt")
            }.forEach { source ->
                val sourceText = source.readText()
                val genericLogSink = Regex(
                    "(?:android\\.util\\.)?Log\\s*\\.",
                )
                val stdoutSink = Regex(
                    "(?:System\\.(?:out|err)\\.|(?<![A-Za-z])println\\s*\\()",
                )

                if (genericLogSink.containsMatchIn(sourceText)) {
                    throw GradleException(
                        "Generic Log.* sink is forbidden in capture-reachable code: " +
                            source.relativeTo(projectDir),
                    )
                }

                if (stdoutSink.containsMatchIn(sourceText)) {
                    throw GradleException(
                        "Generic stdout/stderr sink is forbidden in capture-reachable code: " +
                            source.relativeTo(projectDir),
                    )
                }
            }
        }
    }
}
