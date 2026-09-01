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
        jvmMain {
            dependencies {
                implementation(libs.jts.core)
                implementation(libs.jts.io.common)
            }
            resources.setSrcDirs(resources.srcDirs + "${layout.buildDirectory}/dist/js/productionExecutable/")
        }
        jsMain {
            dependencies {
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
                implementation(libs.jts.io.common)
                implementation(libs.bundles.testing)
                runtimeOnly(libs.junit.platform.launcher)  // https://github.com/gradle/gradle/issues/34512
            }
        }
    }
}

setOverallCoverage(0.0) // only increasing allowed!
