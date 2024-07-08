import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("plugin.js-plain-objects")
}

kotlin {
    jvm {
        withJava()
    }

    js(IR) {
        moduleName = "jbon"
        browser {
            webpackTask {
                output.libraryTarget = "commonjs2"
                output.library = "naksha.jbon"
            }
        }
        useEsModules()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            target.set("es2015")
        }
        nodejs {
        }
        generateTypeScriptDefinitions()
        binaries.library() // gradle jsBrowserProductionLibraryDistribution
        binaries.executable() // gradle jsBrowserProductionWebpack
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                api(project(":here-naksha-lib-base"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation(project(":here-naksha-lib-base"))
            }
        }
        jvmMain {
            jvmToolchain(11)
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                api("org.lz4:lz4-java:1.8.0")
                implementation("org.slf4j:slf4j-simple:2.0.13")
            }
            resources.setSrcDirs(resources.srcDirs + "${layout.buildDirectory}/dist/js/productionExecutable/")
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    val jsProductionLibraryCompileSync = getByName<Task>("jsProductionLibraryCompileSync")
    val jsProductionExecutableCompileSync = getByName<Task>("jsProductionExecutableCompileSync")
    val browserDistribution = getByName<Task>("jsBrowserDistribution")
    val webpackTask = getByName<KotlinWebpack>("jsBrowserProductionWebpack") {
        dependsOn(jsProductionLibraryCompileSync)
    }
    getByName<Task>("jsNodeProductionLibraryDistribution") {
        dependsOn(jsProductionExecutableCompileSync)
    }
    getByName<Task>("jsBrowserProductionLibraryDistribution") {
        dependsOn(jsProductionExecutableCompileSync)
    }
    getByName<ProcessResources>("jvmProcessResources") {
        dependsOn(webpackTask, browserDistribution)
    }
    getByName<ProcessResources>("jvmTestProcessResources") {
        dependsOn(webpackTask, browserDistribution)
    }
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "8g"
    }
    getByName<Jar>("jvmJar") {
        dependsOn(webpackTask)
    }
}
