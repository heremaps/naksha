plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm {
    }
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-model"))

                // Can we get rid of this?
                implementation(libs.google.guava)
                implementation(libs.google.findbugs.jsr305)
                implementation(libs.commons.lang3)
                implementation(libs.google.flatbuffers)
                implementation(libs.bundles.spatial)
                implementation(libs.bundles.jackson)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.bundles.testing)
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
}
setOverallCoverage(0.0) // only increasing allowed!
