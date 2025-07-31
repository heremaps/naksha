import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.shadow)
}

description = gatherDescription()
val mainClassPath = "com.here.naksha.cli.Main"
val fatJarBaseName = "naksha-cli"

kotlin {
    jvmToolchain(23)

    jvm {
        mainRun {
            mainClass.set(mainClassPath)
        }
    }

    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.picocli)
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
        archiveBaseName.set(fatJarBaseName)
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        manifest {
            attributes["Main-Class"] = mainClassPath
        }

        from(kotlin.jvm().compilations.getByName("main").output)

        configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    }

    named("jvmJar") {
        dependsOn(shadowCreate)
    }
}

setOverallCoverage(0.0) // only increasing allowed!