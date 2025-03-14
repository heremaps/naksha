package naksha.psql

import org.testcontainers.containers.GenericContainer

internal data class PsqlTestDockerContainerInfo(
    val container: GenericContainer<*>,
    val config: PgInstanceConfig,
    val shutdownThread: Thread
)