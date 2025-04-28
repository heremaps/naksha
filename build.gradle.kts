import com.vanniktech.maven.publish.SonatypeHost
import java.net.URI

plugins {
    // Shared plugins
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.js.plain.objects) apply false
    alias(libs.plugins.foojay) apply false
    alias(libs.plugins.vanniktechMavenPublish)

    // Only need within root
    // see: https://github.com/johnrengelman/shadow
    alias(libs.plugins.shadow) apply false
}

//configurations.implementation {
//    exclude(module = "commons-logging")
//}

// Create the fat jar for the whole Naksha.
//rootProject.dependencies {
//    // This is needed, otherwise the blank root project will include nothing in the fat jar.
//    implementation(project(":here-naksha-app-service"))
//}
data class MvnInfo(val url: String, val user: String, val password: String)

data class SigningKey(val keyId: String, val key: String, val password: String)

fun Project.getMvnInfo(prefix: String): MvnInfo? {
    val url = getPropertyFromRootProject("${prefix}Url")
    val user = getPropertyFromRootProject("${prefix}User")
    val password = getPropertyFromRootProject("${prefix}Password")
    if (url != null && user != null && password != null) {
        return MvnInfo(url, user, password)
    }
    return null
}

fun Project.getMavenCentralInfo(): MvnInfo? {
    val url = getPropertyFromRootProject("mavenCentralUrl", "ORG_GRADLE_PROJECT_") ?: "https://central.sonatype.com/api/v1/publisher/deployments"
    val user = getPropertyFromRootProject("mavenCentralUsername", "ORG_GRADLE_PROJECT_")
    val password = getPropertyFromRootProject("mavenCentralPassword", "ORG_GRADLE_PROJECT_")
    if (user != null && password != null) {
        return MvnInfo(url, user, password)
    }
    return null
}

fun Project.getSigningKey(): SigningKey? {
    val keyId = getPropertyFromRootProject("signingInMemoryKeyId", "ORG_GRADLE_PROJECT_")
    val key = getPropertyFromRootProject("signingInMemoryKey", "ORG_GRADLE_PROJECT_")
    val pwd = getPropertyFromRootProject("signingInMemoryKeyPassword", "ORG_GRADLE_PROJECT_")
    if (keyId != null && key != null && pwd != null) {
        return SigningKey(keyId, key, pwd)
    }
    return null
}

enum class CleanAndTest {
    // Do not run tests
    OFF,
    // Run jvmTest
    KOTLIN,
}

enum class PublishModule {
    // do NOT publish the module to artifactory
    NO,
    // publish the module to artifactory
    YES
}

val modulesToTest = mapOf(
    Pair("here-naksha-app-service", Pair(CleanAndTest.OFF, PublishModule.NO)),
    Pair("here-naksha-common-http", Pair(CleanAndTest.OFF, PublishModule.NO)),
    Pair("here-naksha-handler-activitylog", Pair(CleanAndTest.OFF, PublishModule.NO)),
    //Pair("here-naksha-handler-http", Pair(CleanAndTest.OFF, PublishModule.NO)),
    Pair("here-naksha-lib-auth", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair("here-naksha-lib-base", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair("here-naksha-lib-core", Pair(CleanAndTest.OFF, PublishModule.NO)),
    Pair("here-naksha-lib-diff", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair("here-naksha-lib-ext-manager", Pair(CleanAndTest.OFF, PublishModule.NO)),
    //Pair("here-naksha-lib-extension", Pair(CleanAndTest.OFF, PublishModule.NO)),
    Pair("here-naksha-lib-geo", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair("here-naksha-lib-handlers", Pair(CleanAndTest.OFF, PublishModule.NO)),
    //Pair("here-naksha-lib-heapcache", Pair(CleanAndTest.OFF, PublishModule.NO)),
    Pair("here-naksha-lib-hub", Pair(CleanAndTest.OFF, PublishModule.NO)),
    Pair("here-naksha-lib-jbon", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair("here-naksha-lib-model", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair("here-naksha-lib-psql", Pair(CleanAndTest.KOTLIN, PublishModule.YES)),
    Pair("here-naksha-lib-view", Pair(CleanAndTest.KOTLIN, PublishModule.NO)),
    Pair("here-naksha-storage-http", Pair(CleanAndTest.OFF, PublishModule.NO)),
)

fun Project.configureVanniktechMavenPublish() {
    println("Configure publishing for: $group:$name:$version")
//    val keyId = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyId")
//    val key = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")
//    val keyPwd = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
//    val centralUser = System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername")
//    val centralPwd = System.getenv("ORG_GRADLE_PROJECT_mavenCentralPassword")
//    println("publish keyId: $keyId, key: $key, keyPwd: $keyPwd, centralUser: $centralUser, centralPwd: $centralPwd")
    val projectRepoURI = "github.com/heremaps/naksha"
    val here = getMvnInfo("here")
    if (here != null) {
        println("\tAdd 'HereMaven' repository, ${here.user}:***@${here.url}")
        publishing {
            repositories {
                maven {
                    name = "HereMaven"
                    url = URI(here.url)
                    credentials.username = here.user
                    credentials.password = here.password
                }
            }
        }
    }
    val sign = getSigningKey()
    val portal = getMavenCentralInfo()
    mavenPublishing {
        if (sign != null && portal != null) {
            println("\tAdd 'MavenCentral' repository, ${portal.user}:***@${portal.url}")
            println("\tConfigure mavenPublishing for 'local' and '${SonatypeHost.CENTRAL_PORTAL}'")
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, true)
            signAllPublications()
        } else {
            println("\tConfigure mavenPublishing for 'local'")
        }
        coordinates(group.toString(), name, version.toString())
        pom {
            name = project.name
            description = project.gatherDescription() ?: throw Exception("No Description for ${project.name}")
            url = "https://${projectRepoURI}"
            licenses {
                license {
                    name = "The Apache License, Version 2.0"
                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            scm {
                connection = "scm:git:https://${projectRepoURI}.git"
                developerConnection = "scm:git:ssh://git@${projectRepoURI}.git"
                url = "https://${projectRepoURI}"
            }
            developers {
                developer {
                    id = "xeus2001"
                    name = "Alexander Lowey-Weber"
                    email = "naksha@here.com"
                    url = "https://github.com/xeus2001"
                }
                developer {
                    id = "gunplar"
                    name = "Phuc Mai"
                    url = "https://github.com/gunplar"
                }
                developer {
                    id = "hirenkp2000"
                    name = "Hiren Patel"
                    url = "https://github.com/hirenkp2000"
                }
                developer {
                    id = "Amaneusz"
                    name = "Jakub Amanowicz "
                    url = "https://github.com/Amaneusz"
                }
            }
        }
        //println("--------- group: $group, version: $version, mavenUrl: $mavenUrl, mavenUser: $mavenUser, mavenPassword: $mavenPassword")
//        artifacts {
//            file("build/libs/${project.name}-${project.version}.jar")
//            file("build/libs/${project.name}-${project.version}-javadoc.jar")
//            file("build/libs/${project.name}-${project.version}-sources.jar")
//        }
    }
}

allprojects {
    group = "io.github.naksha-oss"
    version = getRequiredPropertyFromRootProject("version")

    repositories {
        maven("https://repo.osgeo.org/repository/release/")
        mavenCentral()
        mavenLocal()
    }
}

subprojects {
    //println("configure $group -- $name -- $version")
    val info = modulesToTest[name]
    if (info != null && info.second == PublishModule.YES) {
        apply(plugin = "com.vanniktech.maven.publish")
        configureVanniktechMavenPublish()
    }
}

// Helper, run as `gradle cleanAndTestAll`
fun Task.configureCleanAndTestTasks() {
    modulesToTest.forEach {
        when (it.value.first) {
            CleanAndTest.KOTLIN -> {
                dependsOn(":${it.key}:cleanJvmTest")
                dependsOn(":${it.key}:jvmTest")
            }
            else -> {}
        }
    }
}
tasks.register("cleanAndTestAll") { configureCleanAndTestTasks() }

// Helper, run as `gradle publishToLocal`
fun Task.publishToLocal() {
    modulesToTest.forEach {
        when (it.value.second) {
            PublishModule.YES -> {
                //val project = project(":${it.key}")
                //project.pluginManager.apply("naksha.publish")
                dependsOn(":${it.key}:publishJvmPublicationToMavenLocal")
                dependsOn(":${it.key}:publishKotlinMultiplatformPublicationToMavenLocal")
            }
            else -> {}
        }
    }
}
tasks.register("publishToLocal") { publishToLocal() }

// Helper, run as `gradle publishToHere`
fun Task.publishToHere() {
    getMvnInfo("here") ?:
        throw IllegalArgumentException(
            "Missing 'hereUrl', 'hereUser', and 'herePassword', declare in environment-variable or ~/.gradle/gradle.properties"
        )
    modulesToTest.forEach {
        when (it.value.second) {
            PublishModule.YES -> {
                dependsOn(":${it.key}:clean")
                dependsOn(":${it.key}:publishJvmPublicationToHereMavenRepository")
                dependsOn(":${it.key}:publishKotlinMultiplatformPublicationToHereMavenRepository")
            }
            else -> {}
        }
    }
}
tasks.register("publishToHere") { publishToHere() }

// Helper, run as `gradle publishToCentral`
fun Task.publishToCentral() {
    val sign = getSigningKey()
    if (sign == null) {
        println("To publish to maven central define the following properties in you gradle.properties:")
        println("\tsigningInMemoryKeyId")
        println("\tsigningInMemoryKey")
        println("\tsigningInMemoryKeyPassword")
        println("Or export them as env variables prefixed with 'ORG_GRADLE_PROJECT_':")
        println("\tORG_GRADLE_PROJECT_signingInMemoryKeyId")
        println("\tORG_GRADLE_PROJECT_signingInMemoryKey")
        println("\tORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
        throw IllegalArgumentException("Missing signing key")
    }
    val central = getMavenCentralInfo()
    if (central == null) {
        println("To publish to maven central define the following properties in you gradle.properties:")
        println("\tmavenCentralUsername")
        println("\tmavenCentralPassword")
        println("Or export them as env variables prefixed with 'ORG_GRADLE_PROJECT_':")
        println("\tORG_GRADLE_PROJECT_mavenCentralUsername")
        println("\tORG_GRADLE_PROJECT_mavenCentralPassword")
        throw IllegalArgumentException("Missing central credentials")
    }
    modulesToTest.forEach {
        when (it.value.second) {
            PublishModule.YES -> {
                println("publish to maven central: ${it.key}")
                //dependsOn("${it.key}:clean")
                //dependsOn(":${it.key}:publishJvmPublicationToMavenPortalRepository")
                //dependsOn(":${it.key}:publishKotlinMultiplatformPublicationToMavenPortalRepository")

                //dependsOn(":${it.key}:publishJvmPublicationToMavenCentralRepository")
                //dependsOn(":${it.key}:publishKotlinMultiplatformPublicationToMavenCentralRepository")
                dependsOn(":${it.key}:publishAndReleaseToMavenCentral")
            }
            else -> {}
        }
    }
}
tasks.register("publishToCentral") { publishToCentral() }
