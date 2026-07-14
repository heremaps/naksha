plugins {
    `kotlin-dsl`
}

java {
    // buildSrc is a subproject, navigate to the actual root project's gradle.properties
    val gradleProps = gradle.rootProject.projectDir.parentFile?.resolve("gradle.properties")
        ?: throw Error("Cannot locate root gradle.properties")
    val jvmVersion = gradleProps.readText()
        .split("\n")
        .find { it.trim().startsWith("jvm.target") }
        ?.substringAfter("=")
        ?.trim()
        ?: throw Error("Missing 'jvm.target' property in root gradle.properties")
    toolchain { languageVersion.set(JavaLanguageVersion.of(jvmVersion.toInt())) }
}

group = rootProject.group
version = rootProject.version

repositories {
    maven("https://repo.osgeo.org/repository/release/")
    mavenCentral()
    mavenLocal()
}
