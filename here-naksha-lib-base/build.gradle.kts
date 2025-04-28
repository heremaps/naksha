import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = gatherDescription()

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(kotlin("reflect"))
                // https://github.com/Kotlin/kotlinx-datetime
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
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
                api(libs.lz4.java)
                implementation(libs.jackson.kotlin)
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
            }
        }
//        nativeMain {
//
//        }
//        val desktopMain by creating {
//            dependsOn(commonMain.get())
//        }
//        linuxX64Main.get().dependsOn(desktopMain)
//        mingwX64Main.get().dependsOn(desktopMain)
//        macosX64Main.get().dependsOn(desktopMain)
    }

    jvm {}
    js(IR) {
        outputModuleName = "naksha_base"
        useEsModules()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
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

//    linuxX64("native") {
//        binaries.sharedLib {
//            baseName = "native"
//        }
//    }
//    linuxArm64("native") {
//        binaries.sharedLib {
//            baseName = "native"
//        }
//    }
//    mingwX64("native") {
//        binaries.sharedLib {
//            baseName = "native"
//        }
//    }
//    macosX64("native") {
//        binaries.sharedLib {
//            baseName = "native"
//        }
//    }
//    macosArm64("native") {
//        binaries {
//            sharedLib {
//                baseName = "native"
//            }
//        }
//    }
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
