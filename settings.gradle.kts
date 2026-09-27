pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.mozilla.org/maven2/")
    }
}

rootProject.name = "TahoBrowser"
include(":app")
include(":browser:shell")
include(":browser:runtime")
include(":browser:observation")
include(":capture:domain")
include(":capture:persist")
include(":transfer:core")
include(":transfer:android")
include(":contract:taho-transfer")
