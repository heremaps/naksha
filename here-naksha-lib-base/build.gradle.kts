import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy

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
                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect"))
                // https://github.com/Kotlin/kotlinx-datetime
                api(libs.kotlinx.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.datetime)
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(kotlin("reflect"))
                implementation(libs.fastdouble)
                implementation(libs.jetbrains.annotations)
                api(libs.kotlinx.datetime.get().toString()) {
                   exclude(group = "org.jetbrains.annotations")
                }
                api(libs.lz4.java)
                implementation(libs.jackson.kotlin)
                implementation(libs.gson)
                implementation(libs.jsonio)
                implementation(libs.fastjson)
                // implementation(libs.simdjson) // Ones we have Java 25 !
                // https://mvnrepository.com/artifact/org.slf4j
                api(libs.slf4j.api)
                implementation(libs.slf4j.console)
            }
            resources.setSrcDirs(resources.srcDirs + "${layout.buildDirectory}/dist/js/productionExecutable/")
        }
        jsMain {
            dependencies {
                api(kotlin("stdlib-js"))
                api(kotlin("reflect"))
                api(libs.kotlinx.datetime)
            }
        }
    }
}

setOverallCoverage(0.0) // only increasing allowed!
