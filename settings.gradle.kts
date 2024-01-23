plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "naksha"

include(":here-naksha-lib-jbon")
include(":here-naksha-lib-plv8")
include(":here-naksha-lib-core")
include(":here-naksha-lib-heapcache")
include(":here-naksha-lib-psql")
//include(":here-naksha-lib-extension")
//include(":here-naksha-handler-activitylog")
//include(":here-naksha-handler-http")
//include(":here-naksha-handler-psql")
include(":here-naksha-lib-handlers")
include(":here-naksha-lib-hub")
include(":here-naksha-app-service")
include(":here-naksha-lib-view")
