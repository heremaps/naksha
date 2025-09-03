import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.shadow)
    alias(libs.plugins.asciiDoctorJvmConvert)
    alias(libs.plugins.axion.release)
}

scmVersion {
    tag {
        prefix.set("cli-v")
    }
}

description = gatherDescription()
version = scmVersion.version
val mainCliClass = "com.here.naksha.cli.Main"
val fatJarBaseName = "naksha-cli"

configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}

kotlin {
    jvmToolchain(23)

    jvm {
        mainRun {
            mainClass.set(mainCliClass)
        }
    }

    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.picocli)
                implementation(libs.bundles.logging)
                implementation(project(":here-naksha-lib-base"))
                implementation(project(":here-naksha-lib-model"))
                implementation(project(":here-naksha-lib-psql"))
                implementation(project(":here-naksha-lib-core"))
                compileOnly(libs.picocli.codegen)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.bundles.testing)
                implementation(libs.test.containers)
            }
        }
    }
}

tasks {
    getByName<Jar>("jvmJar") { dependsOn("jvmProcessResources") }
    getByName<ProcessResources>("jvmTestProcessResources") { dependsOn("jvmProcessResources") }
    getByName<Test>("jvmTest") {
        useJUnitPlatform()
        maxHeapSize = "6g"
    }

    register("shadowCreate", ShadowJar::class) {
        group = "Shadow"
        description = "Creates fat jar."
        archiveBaseName.set(fatJarBaseName)
        archiveClassifier.set("")
        archiveVersion.set(scmVersion.version)
        manifest {
            attributes["Main-Class"] = mainCliClass
            attributes["Implementation-Version"] = scmVersion.version
        }
        from(kotlin.jvm().compilations.getByName("main").output)
        configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    }

    register("releaseAndShadow", ShadowJar::class) {
        dependsOn(release)
        group = "Release"
        description = "Performs release and creates shadow jar."
        archiveBaseName.set(fatJarBaseName)
        archiveClassifier.set("")
        archiveVersion.set(scmVersion.undecoratedVersion)
        manifest {
            attributes["Main-Class"] = mainCliClass
            attributes["Implementation-Version"] = scmVersion.undecoratedVersion
        }
        from(kotlin.jvm().compilations.getByName("main").output)
        configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    }

    val generateManpageAsciiDoc by registering(JavaExec::class) {
        group = "Documentation"
        description = "Generate AsciiDoc manpage"
        classpath(
            kotlin.jvm().compilations["main"].output,
            kotlin.jvm().compilations["main"].runtimeDependencyFiles,
            kotlin.jvm().compilations["main"].compileDependencyFiles
        )
        mainClass = "picocli.codegen.docgen.manpage.ManPageGenerator"
        args(
            "com.here.naksha.cli.NakshaCliCommand",
            "--outdir=${layout.buildDirectory.get()}/generated-picocli-docs",
            "-v",
            "-c=com.here.naksha.cli.CommandFactory"
        )
    }

    asciidoctor {
        dependsOn(generateManpageAsciiDoc)
        sourceDir(file("${layout.buildDirectory.get()}/generated-picocli-docs"))
        setOutputDir(file("${layout.buildDirectory.get()}/docs"))
        logDocuments = true
        outputOptions {
            backends("manpage", "html5")
        }
    }
}

setOverallCoverage(0.0) // only increasing allowed!