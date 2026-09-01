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
    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set(mainCliClass)
        }
    }

    sourceSets {
        jvmMain {
            dependencies {
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-psql"))
                api(project(":here-naksha-lib-core"))

                implementation(libs.picocli)
                implementation(libs.bundles.logging)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.bundles.testing)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512

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