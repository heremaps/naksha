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
        moduleName = "base"
        browser {
            webpackTask {
                output.libraryTarget = "commonjs2"
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
                implementation(kotlin("reflect"))
                // https://github.com/Kotlin/kotlinx-datetime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        commonTest {
            // TODO: https://kotlinlang.org/docs/js-running-tests.html
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        jvmMain {
            jvmToolchain(11)
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(kotlin("reflect"))
                api("org.lz4:lz4-java:1.8.0")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
                // https://mvnrepository.com/artifact/org.slf4j
                api("org.slf4j:slf4j-api:2.0.13")
                implementation("org.slf4j:slf4j-simple:2.0.13")
                api("org.lz4:lz4-java:1.8.0")
            }
            resources.setSrcDirs(resources.srcDirs + "$buildDir/dist/js/productionExecutable/")
        }
        jvmTest {
            jvmToolchain(11)
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
            }
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(kotlin("reflect"))
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
