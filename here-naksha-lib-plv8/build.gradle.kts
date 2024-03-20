import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    kotlin("multiplatform").version("1.9.21")
}

kotlin {
    jvm {
        jvmToolchain(11)
        withJava()
    }

    js(IR) {
        moduleName = "plv8"
        browser {
            webpackTask {
                output.libraryTarget = "commonjs2"
            }
        }
        nodejs {
        }
        generateTypeScriptDefinitions()
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(project(":here-naksha-lib-jbon"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        commonMain {
            dependencies {
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(project(":here-naksha-lib-jbon"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.postgresql:postgresql:42.5.4")
            }
            resources.setSrcDirs(resources.srcDirs + "$buildDir/dist/js/productionExecutable/")
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
                implementation("org.testcontainers:postgresql:1.19.4")
                implementation("org.postgresql:postgresql:42.5.4")
                implementation(project(":here-naksha-lib-jbon"))
                implementation("de.bytefish:pgbulkinsert:8.1.3")
            }
        }
        jsMain {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(project(":here-naksha-lib-jbon"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    val webpackTask = getByName<KotlinWebpack>("jsBrowserProductionWebpack")
    val browserDistribution = getByName<Task>("jsBrowserDistribution")
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "8g"
    }
    getByName<Jar>("jvmJar") {
        dependsOn(webpackTask)
        from({
            val list = ArrayList<Any>()
            configurations.runtimeClasspath.get().forEach {
                val f = if (it.isDirectory()) it else zipTree(it)
                list.add(f)
            }
            list
        }).duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    getByName<ProcessResources>("jvmProcessResources") {
        dependsOn(webpackTask)
    }
    getByName<ProcessResources>("jvmTestProcessResources") {
        dependsOn(webpackTask, browserDistribution)
    }
}