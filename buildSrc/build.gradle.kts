plugins {
    `kotlin-dsl`
}

java {
    // buildSrc is a subproject, navigate to the actual root project's gradle.properties
    val gradleProps = gradle.rootProject.projectDir.parentFile?.resolve("gradle.properties")
        ?: throw Error("Cannot locate root gradle.properties")
    val jvmToolchainVersion = gradleProps.readText()
        .split("\n")
        .find { it.trim().startsWith("jvm.toolchain") }
        ?.substringAfter("=")
        ?.trim()
        ?: throw Error("Missing 'jvm.target' property in root gradle.properties")
    println("--------> Use JVM toolchain: $jvmToolchainVersion")
    toolchain { languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion.toInt())) }
}

group = rootProject.group
version = rootProject.version

repositories {
    maven("https://repo.osgeo.org/repository/release/")
    mavenCentral()
    mavenLocal()
}
