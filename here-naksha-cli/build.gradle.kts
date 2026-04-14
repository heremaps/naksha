import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.shadow)
    alias(libs.plugins.axion.release)
}

scmVersion {
    tag {
        prefix.set("cli-v")
    }
}

description = gatherDescription()
version = scmVersion.version
val mainCliClass = "com.here.naksha.cli.Main"
val fatJarBaseName = "naksha-cli"

configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}

java {
    setSourceCompatibility(23)
    setTargetCompatibility(23)
}

kotlin {

    jvm {
        mainRun {
            mainClass.set(mainCliClass)
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
        }
    }

    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.picocli)
                implementation(libs.bundles.logging)
                implementation(project(":here-naksha-lib-base"))
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-psql"))
                implementation(project(":here-naksha-lib-core"))
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
                implementation(libs.test.containers)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512
            }
        }
    }
}

tasks {
    getByName<Jar>("jvmJar") { dependsOn("jvmProcessResources") }
    getByName<ProcessResources>("jvmTestProcessResources") { dependsOn("jvmProcessResources") }
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "6g"
    }

    named<ShadowJar>("shadowJar") {
        group = "Shadow"
        description = "Creates fat jar."
        archiveBaseName = fatJarBaseName
        archiveClassifier = ""
        archiveVersion = scmVersion.version
        manifest {
            attributes["Main-Class"] = mainCliClass
            attributes["Implementation-Version"] = scmVersion.version
        }
    }

    register("releaseAndShadow", ShadowJar::class) {
        dependsOn(release)
        group = "Release"
        description = "Performs release and creates shadow jar."
        archiveBaseName = fatJarBaseName
        archiveClassifier = ""
        archiveVersion = scmVersion.undecoratedVersion
        manifest {
            attributes["Main-Class"] = mainCliClass
            attributes["Implementation-Version"] = scmVersion.undecoratedVersion
        }
        from(kotlin.jvm().compilations.getByName("main").output)
        configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    }
}

setOverallCoverage(0.0) // only increasing allowed!