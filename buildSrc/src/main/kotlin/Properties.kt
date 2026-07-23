import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.withType

fun getJvmTargetVersion(project: Project): String {
    var jmvTargetVersion = project.rootProject.findProperty("jvm.${project.name.replace('-', '_')}") as? String?
    if (jmvTargetVersion == null) jmvTargetVersion = project.rootProject.findProperty("jvm.target") as? String
    if (jmvTargetVersion == null) throw Error("Missing 'jvm.target' property, add it into gradle.properties")
    return jmvTargetVersion
}

fun getJvmToolchainVersion(project: Project): String {
    return project.rootProject
        .findProperty("jvm.toolchain") as? String ?: throw Error("Missing 'jvm.toolchain' property, add it into gradle.properties")
}

fun getJvmTargetName(project: Project): String {
    return "JVM_${getJvmTargetVersion(project)}"
}

fun Project.getPropertyFromRootProject(propertyKey: String, envPrefix: String? = null): String? {
    val envName = if (envPrefix!=null) "$envPrefix$propertyKey" else propertyKey
    return System.getenv(envName) ?: this.rootProject.properties[propertyKey] as String?
}

fun Project.getRequiredPropertyFromRootProject(propertyKey: String): String {
    return System.getenv(propertyKey) ?: this.rootProject.properties[propertyKey] as? String ?: throw IllegalArgumentException(
        """
        Not found required property: $propertyKey. 
        Check your 'gradle.properties' file (in both project and ~/.gradle directory)
        """.trimIndent()
    )
}