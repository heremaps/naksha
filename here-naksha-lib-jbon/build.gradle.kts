import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    jvm {
    }
    js(IR) {
        outputModuleName = "naksha_jbon"
        useEsModules()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            target.set("es2015")
        }
        nodejs {
            compilerOptions {
                moduleKind = JsModuleKind.MODULE_ES
                moduleName = "naksha_jbon"
                sourceMap = true
                useEsClasses = true
                sourceMapNamesPolicy = JsSourceMapNamesPolicy.SOURCE_MAP_NAMES_POLICY_SIMPLE_NAMES
                sourceMapEmbedSources = JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
            }
            generateTypeScriptDefinitions()
            binaries.library()
            binaries.executable()
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib"))
                api(libs.kotlinx.datetime)
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-geo"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.datetime)
                implementation(project(":here-naksha-lib-base"))
                implementation(project(":here-naksha-lib-geo"))
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                api(libs.lz4.java)
                implementation(libs.slf4j.console)
            }
            resources.setSrcDirs(resources.srcDirs + "${layout.buildDirectory}/dist/js/productionExecutable/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlintest.runner.junit5)
                runtimeOnly(libs.junit.jupiter.engine)
            }
        }
    }
}

tasks {
    getByName<Task>("jsNodeProductionLibraryDistribution") {
        dependsOn("jsProductionLibraryCompileSync", "jsProductionExecutableCompileSync")
    }
    // Release
    getByName<ProcessResources>("jvmProcessResources") {
        dependsOn("jsNodeProductionLibraryDistribution" ) // "jsBrowserDistribution"
    }
    getByName<Jar>("jvmJar") { dependsOn("jvmProcessResources") }
    // Test
    getByName<ProcessResources>("jvmTestProcessResources") { dependsOn("jvmProcessResources") }
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "8g"
    }
}
setOverallCoverage(0.0) // only increasing allowed!