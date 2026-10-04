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

        // A JVM unit test cannot enumerate package-level declarations, so
        // nothing else stops someone re-adding `internal val TahoGold =
        // Color(0xFFE2B44A)` as a shim -- verified by mutation: the whole suite
        // stays green, because no test names the old tokens and every call site
        // already resolves. The rule requires a declaration, so a mention in
        // prose or a call site cannot trip it.
        //
        // TahoWarn is deliberately absent: it is a retained name aliasing amber,
        // not a retired one. TahoDisplay is present even though it was a font
        // family rather than a colour, because Task 2 deleted it for the same
        // reason these did and an alias is an alias.
        val retiredShellTokens = listOf(
            "TahoBg", "TahoSheet", "TahoGold", "TahoGoldHi", "TahoText", "TahoMuted",
            "TahoFaint", "TahoNeutral", "TahoOk", "TahoInfo", "TahoError", "TahoJsonNum",
            "TahoHairline", "TahoHairlineStrong", "TahoSurfaceRow", "TahoSurfaceRowHover",
            "TahoSurfaceControl", "TahoDisplay",
        )

        // Scoped to the module directory, not to src/main: the include pattern
        // is relative to the tree root, so `fileTree("browser/shell/src/main")
        // { include("src/**/*.kt") }` resolves to
        // browser/shell/src/main/src/**/*.kt and matches nothing -- a rule that
        // can never fire. Caught by mutation, not by reading.
        fileTree("browser/shell") {
            include("src/**/*.kt")
        }.forEach { source ->
            val sourceText = source.readText()
            retiredShellTokens.forEach { token ->
                val redeclaration = Regex("""\bval\s+$token\b""")
                if (redeclaration.containsMatchIn(sourceText)) {
                    throw GradleException(
                        "retired token $token reintroduced in ${source.relativeTo(projectDir)}",
                    )
                }
            }
        }
    }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
