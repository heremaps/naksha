plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // we use this, ones we're back to java, currently it breaks the multi-platform build!
    // alias(libs.plugins.jmh)
}

description = gatherDescription()

kotlin {
    jvm { }
    js { }
    sourceSets {
        commonMain {
            dependencies {
                api(kotlin("reflect"))
                api(libs.kotlinx.datetime.get().toString()) {
                    exclude(group = "org.jetbrains.annotations")
                }
            }
        }
        jvmMain {
            dependencies {
                api(libs.jetbrains.annotations)
                api(libs.lz4.java)
                api(libs.slf4j.api) // https://mvnrepository.com/artifact/org.slf4j

                implementation(libs.jackson.kotlin)
                implementation(libs.fastdouble)
                implementation(libs.gson)
                implementation(libs.jsonio)
                implementation(libs.fastjson)
                implementation(libs.slf4j.console)
                // implementation(libs.simdjson) // Ones we have Java 25 !
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
                implementation(libs.bundles.testing)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512
            }
        }
    }
}

setOverallCoverage(0.0) // only increasing allowed!
