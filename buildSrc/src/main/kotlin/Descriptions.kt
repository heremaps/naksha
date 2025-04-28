import org.gradle.api.Project

// We do this, because when we add this directly into the build.gradle.kts,
//   then multiplatform artifacts do not have a descriptions, which is not
//   accepted by maven central!
private val Descriptions = mapOf(
    "here-naksha-lib-base" to "Naksha library, providing basic cross-platform capabilities (extending the raw Kotlin features).",
    "here-naksha-lib-auth" to "Naksha library, provides helper classes to perform authorization against Wikvaya UPM (User Permission Management) authorization matrix.",
    "here-naksha-lib-geo" to "Naksha library, adding GeoJSON support and basic spatial operation support.",
    "here-naksha-lib-jbon" to "Naksha library, adding support to encode and decode JBON (Java Binary Object Notation).",
    "here-naksha-lib-model" to "Naksha library, adding the Storage-Abstraction-Layer of Naksha, this is the base of all Naksha storage operations. It defines interfaces, helper classes, abstract base classes, and more, needed to use storage implementations or assisting in making new storage implementations.",
    "here-naksha-lib-psql" to "Naksha library, implementation of the Naksha Storage-Abstraction-Layer."
)

fun Project.gatherDescription(): String
  = Descriptions[this.name] ?: throw IllegalArgumentException("Description for project ${this.name} not found")
