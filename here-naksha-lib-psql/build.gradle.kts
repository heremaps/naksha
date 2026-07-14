import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.js.plain.objects)
}

description = gatherDescription()

kotlin {
    jvm {
    }
    js(IR) {
        outputModuleName = "naksha_psql"
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
                implementation(kotlin("stdlib"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation(libs.kotlinx.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(project(":here-naksha-common-test"))
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.datetime)
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-geo"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation(libs.commons.lang3)
                implementation(libs.postgres)
                implementation(libs.test.containers.postgres)
                implementation(libs.commons.dbutils)
                implementation(libs.jts.core)
                implementation(libs.jts.io.common)

                implementation(libs.kotlinx.datetime)
                implementation(libs.postgres)
                implementation(libs.caffeine)
            }
            // TODO: We should replace ${project.buildDir} with ${layout.buildDirectory}, but this is not the same:
            // println("------------ ${project.buildDir}/dist/js/productionExecutable/")
            // println("------------ ${layout.buildDirectory}/dist/js/productionExecutable/")
            resources.setSrcDirs(resources.srcDirs + "${project.rootDir}/here-naksha-lib-psql/build/dist/js/productionLibrary/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlintest.runner.junit5)
                runtimeOnly(libs.junit.jupiter.engine)
                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.params)
                implementation(libs.slf4j.api)
                implementation(libs.slf4j.console)
                implementation(libs.test.containers.postgres)
                implementation(libs.postgres)
                implementation(libs.mockito)
                implementation(libs.mockito.kotlin)
                implementation(libs.spatial4j)
                runtimeOnly(libs.junit.platform.launcher) // https://github.com/gradle/gradle/issues/34512
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

                implementation(libs.kotlinx.datetime)
                //implementation(npm("postgres", "3.4.4"))
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
        val dbUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
        if (dbUrl != null) environment("NAKSHA_TEST_PSQL_DB_URL", dbUrl)
    }
}
setOverallCoverage(0.0) // only increasing allowed!

tasks.matching { it.name == "jsNodeTest" }.configureEach {
    enabled = false
}
