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

kotlin {
    jvmToolchain(23)

    jvm {
        mainRun {
            mainClass.set(mainCliClass)
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

    val shadowCreate by registering(ShadowJar::class) {
        group = "Shadow"
        description = "Creates fat jar"
        archiveBaseName.set(fatJarBaseName)
        archiveClassifier.set("")
        archiveVersion.set(scmVersion.version)
        manifest {
            attributes["Main-Class"] = mainCliClass
            attributes["Implementation-Version"] = scmVersion.version
        }
        from(kotlin.jvm().compilations.getByName("main").output)
        configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    }
}

setOverallCoverage(0.0) // only increasing allowed!