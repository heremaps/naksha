package naksha.psql

import org.testcontainers.containers.GenericContainer

/**
 * Information about a running PostgresQL docker instance.
 * @since 3.0
 */
data class PgDockerContainerInfo(
    /**
     * The docker container.
     * @since 3.0
     */
    val container: GenericContainer<*>,

    /**
     * The instance configuration that can be used to initialize a storage for this container. Should be set to [PgConfig.master].
     * @since 3.0
     */
    val config: PgInstanceConfig,

    /**
     * A thread registered as [shutdown hook][Runtime.addShutdownHook].
     * @since 3.0
     */
    internal val shutdownThread: Thread
)