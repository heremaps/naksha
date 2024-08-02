package naksha.psql

import naksha.geo.SpGeometry
import naksha.model.SessionOptions

/**
 * PostgresQL utility and factory functions. They are implemented differently on every platform.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PgPlatform {
    companion object PgPlatformCompanion {
        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts the literal parts to merge and quote.
         * @return the quoted literal, _null_ if there is no platform specific implementation (fallback to default implementation).
         */
        internal fun quote_literal(vararg parts: String): String?

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         * @return the quoted identifier, _null_ if there is no platform specific implementation (fallback to default implementation).
         */
        internal fun quote_ident(vararg parts: String): String?

        /**
         * Calculates the partition number between 0 and 255. This is the unsigned value of the first byte of the MD5 hash above the given feature-id. When there are less than 256 partitions, the value must be divided by the number of partitions, and the rest addresses the partition, for example for 4 partitions do `partitionNumber(id) % 4`, what will be a value between 0 and 3.
         *
         * In PVL8 this is implemented using the native code as `get_byte(digest(id,'md5'),0)`, which is as well what the partitioning
         * statement will do.
         * @param featureId the feature id.
         * @return the partition number of the feature, a value between 0 and 255.
         */
        fun partitionNumber(featureId: String): Int

        /**
         * Returns the instance.
         * @param host the PostgresQL server host.
         * @param port the PostgresQL server port.
         * @param database the database to connect to.
         * @param user the user to authenticate with.
         * @param password the password to authenticate with.
         * @param readOnly if all connections to the host must read-only (the host is a read-replica).
         * @return the instance that represents this host.
         * @throws UnsupportedOperationException if executed in `PLV8` extension.
         */
        fun getInstance(
            host: String,
            port: Int = 5432,
            database: String,
            user: String,
            password: String,
            readOnly: Boolean = false
        ): PgInstance

        /**
         * Returns the instance for the given JDBC URL. Not supported by all environments, for example `PLV8` does not support this.
         * @param url the JDBC URL, for example `jdbc:postgresql://foo.com/bar_db?user=postgres&password=password`
         * @throws UnsupportedOperationException if executed in `PLV8` environment.
         */
        fun getInstance(url: String): PgInstance

        /**
         * Creates a new cluster configuration. Clusters are not supported by all environments, for example `PLV8` does not support them.
         * @param master the master PostgresQL server.
         * @param replicas the read-replicas; if any.
         * @throws UnsupportedOperationException if executed in `PLV8` environment.
         */
        fun newCluster(master: PgInstance, vararg replicas: PgInstance): PgCluster

        /**
         * Creates a new PostgresQL storage engine. The [PgStorage] is implemented very differently on every platform.
         * @param cluster the PostgresQL server cluster to use.
         * @param schemaName the name of the schema.
         */
        fun newStorage(cluster: PgCluster, schemaName: String): PgStorage

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        fun isPlv8(): Boolean

        /**
         * Returns the [PLV8 storage singleton](https://plv8.github.io/).
         * @return the [PLV8 storage singleton](https://plv8.github.io/).
         * @throws UnsupportedOperationException if called, when [isPlv8] returns _false_.
         */
        fun getPlv8(): PgStorage

        /**
         * Initializes a test-storage to execute tests. If the storage is already initialized, does nothing. Do guarantee that a new
         * storage is initialized, do:
         * ```kotlin
         * if (!PgUtil.initTestStorage(options, params)) {
         *   PgUtil.getTestStorage().close()
         *   check(PgUtil.initTestStorage(options, params))
         * }
         * // The test storage will be freshly initialized!
         * ```
         * @param defaultOptions the default options for new connections.
         * @param params optional parameters to be forwarded to the test engine.
         * @return _true_ if a new test-storage was created; _false_ if there is already an existing storage.
         * @throws UnsupportedOperationException if this platform does not support running tests.
         */
        fun initTestStorage(defaultOptions: SessionOptions, params: Map<String, *>? = null): Boolean

        /**
         * Returns the existing test-storage to execute tests. If no test storage exists yet, creates a new test storage.
         * @return the test-storage.
         * @throws UnsupportedOperationException if this platform does not support running tests.
         */
        fun getTestStorage(): PgStorage

        /**
         * Create a new test-storage to execute tests.
         * @return the test-storage.
         * @throws UnsupportedOperationException if this platform does not support running tests.
         */
        fun newTestStorage(): PgStorage

        /**
         * Decode a GeoJSON geometry from encoded bytes.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        fun decodeGeometry(bytes: ByteArray?, flags: Int): SpGeometry?
        // TODO: In Java use JTS, in PLV8 use PostGis functions!

        /**
         * Encodes the given GeoJSON geometry into bytes.
         * @param geometry the geometry to encode.
         * @param flags the codec flags.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        fun encodeGeometry(geometry: SpGeometry?, flags: Int): ByteArray
        // TODO: In Java use JTS, in PLV8 use PostGis functions!
    }
}