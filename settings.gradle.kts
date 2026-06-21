pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.fabric.io/public") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven { url = uri(rootDir.resolve("local-maven")) }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
        maven { url = uri("https://maven.fabric.io/public") }
        // DJI SDK Maven Repository
        maven { url = uri("https://maven.dji.com/dji/maven") }
    }
}

rootProject.name = "djifly"
include(":app")

// UXSDK is large and slow to configure. Use the cached AAR by default; opt in when rebuilding it.
if (providers.gradleProperty("useUxsdkSource").orNull == "true") {
    include(":android-sdk-v5-uxsdk")
    project(":android-sdk-v5-uxsdk").projectDir =
        file("Mobile-SDK-Android-V5/SampleCode-V5/android-sdk-v5-uxsdk")
}
