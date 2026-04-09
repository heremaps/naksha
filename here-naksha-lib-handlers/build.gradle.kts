plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

java {
    setSourceCompatibility(11)
    setTargetCompatibility(11)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":here-naksha-lib-core"))
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-view"))
                implementation(project(":here-naksha-storage-http"))

                implementation(libs.commons.lang3)
                implementation(libs.commons.dbutils)
                implementation(libs.bundles.jackson)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
                //implementation(testFixtures(project(":here-naksha-lib-core")))
                runtimeOnly(libs.junit.platform.launcher)  // https://github.com/gradle/gradle/issues/34512
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
}
setOverallCoverage(0.0) // only increasing allowed!
