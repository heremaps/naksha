import org.gradle.api.Project

// We do this, because when we add this directly into the build.gradle.kts,
//   then multiplatform artifacts do not have a descriptions, which is not
//   accepted by maven central!
private val Descriptions = mapOf(
    "here-naksha-app-service" to "TBD",
    "here-naksha-common-http" to "TBD",
    "here-naksha-handler-activitylog" to "Naksha handler, adds downward compatibility to XYZ-Hub activity-log.",
    //"here-naksha-handler-http" to "TBD",
    "here-naksha-lib-auth" to "Naksha library, provides helper classes to perform authorization against Wikvaya UPM (User Permission Management) authorization matrix.",
    "here-naksha-lib-base" to "Naksha library, providing basic cross-platform capabilities (extending the raw Kotlin features).",
    "here-naksha-lib-core" to "Naksha library, core parts of Naksha-Hub.",
    "here-naksha-lib-diff" to "Naksha library, provide tools to calculate differences, patched, and to apply them.",
    "here-naksha-lib-ext-manager" to "Naksha library, the Extension-Manager library to be included in applications to load extensions.",
    //"here-naksha-lib-extension" to "TBD",
    "here-naksha-lib-geo" to "Naksha library, adding GeoJSON support and basic spatial operation support.",
    "here-naksha-lib-handlers" to "TBD",
    //"here-naksha-lib-heapcache" to "TBD",
    "here-naksha-lib-hub" to "TBD",
    "here-naksha-lib-jbon" to "Naksha library, adding support to encode and decode JBON (Java Binary Object Notation).",
    "here-naksha-lib-json" to "Naksha library, adding support to encode and decode JSON.",
    "here-naksha-lib-model" to "Naksha library, adding the Storage-Abstraction-Layer of Naksha, this is the base of all Naksha storage operations. It defines interfaces, helper classes, abstract base classes, and more, needed to use storage implementations or assisting in making new storage implementations.",
    "here-naksha-lib-psql" to "Naksha library, implementation of the Naksha Storage-Abstraction-Layer.",
    "here-naksha-lib-view" to "Naksha library, adding capabilities to combine multiple storages, maps, collections into a single virtual view.",
    "here-naksha-storage-http" to "TBD",
    "here-naksha-cli" to "The Naksha CLI tool allows users to interact with Naksha storage.",
    "here-naksha-lib-ns-util" to "Naksha library offering support for breaking change on meta, delta namespaces"
)

fun Project.gatherDescription(): String
  = Descriptions[this.name] ?: throw IllegalArgumentException("Description for project ${this.name} not found")
