import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("plugin.js-plain-objects")
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

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
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

                implementation("org.apache.commons:commons-lang3:3.12.0")
                implementation("org.postgresql:postgresql:42.5.4")
                implementation("org.testcontainers:postgresql:1.19.4")
                implementation("commons-dbutils:commons-dbutils:1.7")
                implementation("org.locationtech.jts:jts-core:1.19.0")
                implementation("org.locationtech.jts.io:jts-io-common:1.19.0")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.postgresql:postgresql:42.5.4")
            }
            // TODO: We should replace ${project.buildDir} with ${layout.buildDirectory}, but this is not the same:
            // println("------------ ${project.buildDir}/dist/js/productionExecutable/")
            // println("------------ ${layout.buildDirectory}/dist/js/productionExecutable/")
            resources.setSrcDirs(resources.srcDirs + "${project.rootDir}/here-naksha-lib-psql/build/dist/js/productionLibrary/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
                implementation("org.slf4j:slf4j-api:2.0.13")
                implementation("org.slf4j:slf4j-simple:2.0.13")
                implementation("org.testcontainers:postgresql:1.19.4")
                implementation("org.postgresql:postgresql:42.5.4")
                implementation("org.mockito:mockito-core:5.8.0")
                implementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
                implementation("org.locationtech.spatial4j:spatial4j:0.8")
            }
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                api(project(":here-naksha-lib-base"))
                api(project(":here-naksha-lib-jbon"))
                api(project(":here-naksha-lib-model"))
                api(project(":here-naksha-lib-geo"))

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
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