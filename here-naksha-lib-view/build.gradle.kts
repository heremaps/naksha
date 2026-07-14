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
                api(project(":here-naksha-lib-core"))
                implementation(project(":here-naksha-lib-model"))

                implementation(libs.commons.lang3)
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":here-naksha-common-test"))
                implementation(libs.bundles.testing)
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-psql"))
                implementation(libs.jts.core)
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
