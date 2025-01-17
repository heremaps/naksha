import org.gradle.api.Project

fun Project.getRequiredPropertyFromRootProject(propertyKey: String): String {
    return this.rootProject.properties[propertyKey] as? String ?: throw IllegalArgumentException(
        """
        Not found required property: $propertyKey. 
        Check your 'gradle.properties' file (in both project and ~/.gradle directory)
        """.trimIndent()
    )
}