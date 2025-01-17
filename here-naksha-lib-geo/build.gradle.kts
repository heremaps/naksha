import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("plugin.js-plain-objects")
    id("naksha.publish")
    id("naksha.java")

    // uncomment spotless to add license comments
    // id("naksha.spotless-kotlin")
}

kotlin {
    jvm {
        withJava()
    }

    js(IR) {
        moduleName = "naksha_geo"
        useEsModules()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            target.set("es2015")
        }
        nodejs {
            compilerOptions {
                moduleKind = JsModuleKind.MODULE_ES
                moduleName = "naksha_geo"
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
                implementation(kotlin("stdlib-common"))
                implementation(Lib.kotlinx_datetime)
                implementation(project(":here-naksha-lib-base"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(Lib.kotlinx_datetime)
            }
        }
        jvmMain {
            jvmToolchain(11)
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(project(":here-naksha-lib-base"))
                implementation(Lib.jts_core)
            }
            resources.setSrcDirs(resources.srcDirs + "$buildDir/dist/js/productionExecutable/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(Lib.jts_io_common)
                implementation(Lib.kotlintest_runner_junit5)
                runtimeOnly(Lib.junit_jupiter_engine)
                implementation(Lib.junit_jupiter_api)
                implementation(Lib.junit_params)
            }
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(project(":here-naksha-lib-base"))
            }
        }
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
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