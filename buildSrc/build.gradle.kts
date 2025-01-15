plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = rootProject.group
version = rootProject.version

repositories {
    maven("https://plugins.gradle.org/m2/")
}

//apply(plugin = "maven-publish")

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.22.0")
    implementation("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:2.0.20")
    implementation("org.jetbrains.kotlin.plugin.js-plain-objects:org.jetbrains.kotlin.plugin.js-plain-objects.gradle.plugin:2.0.20")
}