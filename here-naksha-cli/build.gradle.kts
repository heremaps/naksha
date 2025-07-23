import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.shadow)
}

description = gatherDescription()
val mainClass = "com.here.naksha.cli.Main"
val fatJarBaseName = "naksha-cli"

kotlin {
    jvmToolchain {
        this.languageVersion.set(JavaLanguageVersion.of(21))
    }

    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.picocli)
                implementation(project(":here-naksha-lib-base"))
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-psql"))
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
                implementation(libs.test.containers)
                implementation(project(":here-naksha-lib-psql"))
            }
        }
    }

    jvm {
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
            attributes["Main-Class"] = mainClass
        }

        from(kotlin.jvm().compilations.getByName("main").output)

        configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    }

    named("build") {
        dependsOn(shadowCreate)
    }
}

setOverallCoverage(0.0) // only increasing allowed!