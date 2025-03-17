import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("plugin.js-plain-objects")
    id("naksha.java")
    id("naksha.publish")

    // uncomment spotless to add license comments
    // id("naksha.spotless-kotlin")
}

kotlin {
    jvm {
        withJava()
    }

    js(IR) {
        moduleName = "naksha_psql"
        useEsModules()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            target.set("es2015")
        }
        nodejs {
            compilerOptions {
                moduleKind = JsModuleKind.MODULE_ES
                moduleName = "naksha_psql"
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
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation(Lib.kotlinx_datetime)
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
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation(Lib.commons_lang3)
                implementation(Lib.postgres)
                implementation(Lib.test_containers_postgres)
                implementation(Lib.commons_dbutils)
                implementation(Lib.jts_core)
                implementation(Lib.jts_io_common)

                implementation(Lib.kotlinx_datetime)
                implementation(Lib.postgres)
                implementation(Lib.caffeine)
            }
            // TODO: We should replace ${project.buildDir} with ${layout.buildDirectory}, but this is not the same:
            // println("------------ ${project.buildDir}/dist/js/productionExecutable/")
            // println("------------ ${layout.buildDirectory}/dist/js/productionExecutable/")
            resources.setSrcDirs(resources.srcDirs + "${project.rootDir}/here-naksha-lib-psql/build/dist/js/productionLibrary/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(Lib.kotlintest_runner_junit5)
                runtimeOnly(Lib.junit_jupiter_engine)
                implementation(Lib.junit_jupiter_api)
                implementation(Lib.junit_params)
                implementation(Lib.slf4j_api)
                implementation(Lib.slf4j_console)
                implementation(Lib.test_containers_postgres)
                implementation(Lib.postgres)
                implementation(Lib.mockito)
                implementation(Lib.mockito_kotlin)
                implementation(Lib.spatial4j)

                // Include JMH and JMH annotation processor.
                implementation("org.openjdk.jmh:jmh-core:1.37")
                implementation("org.openjdk.jmh:jmh-generator-annprocess:1.37")
            }
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation(Lib.kotlinx_datetime)
                //implementation(npm("postgres", "3.4.4"))
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
        dependsOn(
            ":here-naksha-lib-base:jsNodeProductionLibraryDistribution",
            ":here-naksha-lib-geo:jsNodeProductionLibraryDistribution",
            ":here-naksha-lib-jbon:jsNodeProductionLibraryDistribution",
            ":here-naksha-lib-model:jsNodeProductionLibraryDistribution",
            "jsNodeProductionLibraryDistribution"
        )
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
