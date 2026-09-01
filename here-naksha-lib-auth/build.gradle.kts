plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm { }
    js { }
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":here-naksha-lib-base"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.bundles.testing)
                implementation(libs.slf4j.console)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512
            }
        }
    }
}

setOverallCoverage(0.0) // only increasing allowed!