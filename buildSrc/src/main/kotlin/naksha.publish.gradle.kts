import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.`maven-publish`
import java.net.URI

plugins {
    id("java")
    id("maven-publish")
}

group = "com.here.naksha"
version = rootProject.properties["version"] as String

val projectRepoURI = getRequiredPropertyFromRootProject("projectRepoURI")
val mavenUrl = getRequiredPropertyFromRootProject("mavenUrl")
val mavenUser = getRequiredPropertyFromRootProject("mavenUser")
val mavenPassword = getRequiredPropertyFromRootProject("mavenPassword")

publishing {
    repositories {
        maven {
            url = URI(mavenUrl)
            credentials.username = mavenUser
            credentials.password = mavenPassword
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])
            pom {
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
            }
        }

        artifacts {
            file("build/libs/${project.name}-${project.version}.jar")
            file("build/libs/${project.name}-${project.version}-javadoc.jar")
            file("build/libs/${project.name}-${project.version}-sources.jar")
        }
    }
}