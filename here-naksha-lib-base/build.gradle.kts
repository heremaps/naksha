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
    jvm {}
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
            jvmToolchain(11)
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

    js(IR) {
        outputModuleName = "naksha_base"
        useEsModules()
        compilerOptions {
            target.set("es2015")
        }
        nodejs {
            compilerOptions {
                moduleKind = JsModuleKind.MODULE_ES
                moduleName = "naksha_base"
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
//    getByName<JavaCompile>("jvmCompile") {
//        options.annotationProcessorPath = configurations
//        options.jm jmhAnnotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.36'
//    }
    getByName<Jar>("jvmJar") { dependsOn("jvmProcessResources") }
    // Test
    getByName<ProcessResources>("jvmTestProcessResources") { dependsOn("jvmProcessResources") }
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "8g"
    }
}
setOverallCoverage(0.0) // only increasing allowed!
