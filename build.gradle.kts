// Top-level build file where you can add configuration options common to all sub-projects/modules.
apply(from = rootProject.file("dependencies.gradle"))

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.fabric.io/public") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.8.2")
        classpath(kotlin("gradle-plugin", version = project.extra["KOTLIN_VERSION"].toString()))
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
