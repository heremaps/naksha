package naksha.psql

import naksha.geo.SpGeometry
import naksha.model.SessionOptions
import java.security.MessageDigest

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PgPlatform {
    actual companion object PgPlatformCompanion {
        /**
         * Special parameter only for JVM storage to install the needed database SQL code in this version. The value is expected to be a
         * [naksha.model.NakshaVersion].
         */
        const val VERSION: String = "version"

        /**
         * A parameter that can be given to [getTestStorage] to not start a docker container, but to connect the test storage against an
         * existing database with this URL.
         *
         * If not given, this parameter is auto-detected from the environment variable named `NAKSHA_TEST_PSQL_DB_URL`.
         *
         * @see PgTest
         */
        const val TEST_URL = "test_url"

        /**
         * A parameter that can be given to [getTestStorage] to not start a docker container, but to connect against the given instance. The value must be a [PgInstance].
         *
         * @see PgTest
         */
        const val TEST_INSTANCE = "test_instance"

        @JvmStatic
        internal actual fun quote_literal(vararg parts: String): String? = null

        @JvmStatic
        internal actual fun quote_ident(vararg parts: String): String? = null

        @JvmStatic
        private val md5Digest = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

        /**
         * Calculates the partition number between 0 and 255. This is the unsigned value of the first byte of the MD5 hash above the
         * given feature-id. When there are less than 256 partitions, the value must be divided by the number of partitions and the rest
         * addresses the partition, for example for 4 partitions we get `partitionNumber(id) % 4`, what will be a value between 0 and 3.
         * In PVL8 this is implemented using the native code as `get_byte(digest(id,'md5'),0)`, which is as well what the partitioning
         * statement will do.
         * @param featureId the feature id.
         * @return the partition number of the feature, a value between 0 and 255.
         */
        @JvmStatic
        actual fun partitionNumber(featureId: String): Int {
            val digest = md5Digest.get()
            digest.reset()
            digest.update(featureId.toByteArray(Charsets.UTF_8))
            val hash = digest.digest()
            return hash[0].toInt() and 0xff
        }

        /**
         * Returns the instance.
         * @param host the PostgresQL server host.
         * @param port the PostgresQL server port.
         * @param database the database to connect to.
         * @param user the user to authenticate with.
         * @param password the password to authenticate with.
         * @param readOnly if all connections to the host must read-only (the host is a read-replica).
         * @return the instance that represents this host.
         */
        @JvmStatic
        actual fun getInstance(
            host: String,
            port: Int,
            database: String,
            user: String,
            password: String,
            readOnly: Boolean
        ): PgInstance = PsqlInstance.get(host, port, database, user, password, readOnly)

        /**
         * Returns the instance for the given JDBC URL.
         * @param url the JDBC URL, for example `jdbc:postgresql://foo.com/bar_db?user=postgres&password=password`
         */
        @JvmStatic
        actual fun getInstance(url: String): PgInstance = PsqlInstance.get(url)

        /**
         * Creates a new cluster configuration.
         */
        @JvmStatic
        actual fun newCluster(master: PgInstance, vararg replicas: PgInstance): PgCluster =
            PsqlCluster(master, replicas.toMutableList())

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        @JvmStatic
        actual fun isPlv8(): Boolean = false

        /**
         * Returns the [PLV8 extension](https://plv8.github.io/) storage.
         * @return the [PLV8 extension](https://plv8.github.io/) storage; _null_ if this code is not executed within PostgresQL database.
         * @throws UnsupportedOperationException if called, when [isPlv8] returns _false_.
         */
        @JvmStatic
        actual fun getPlv8(): PgStorage {
            throw UnsupportedOperationException("PgUtil.getPlv8: This is no PLV8 extension")
        }

        @JvmStatic
        actual fun newStorage(cluster: PgCluster, schemaName: String): PgStorage {
            require(cluster is PsqlCluster) { "The Java PSQL storage only works with PsqlCluster instances, please use PgUtil.newCluster" }
            return PsqlStorage(cluster, schemaName)
        }

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
         */
        @JvmStatic
        actual fun initTestStorage(defaultOptions: SessionOptions, params: Map<String, *>?): Boolean {
            var testStorage = PsqlTestStorage.storage.get()
            if (testStorage != null) return false
            testStorage = PsqlTestStorage.getTestOrInitStorage(defaultOptions, params)
            return testStorage === PsqlTestStorage.storage.get()
        }

        /**
         * Returns the existing test-storage to execute tests. If no test storage exists yet, creates a new test storage.
         * @return the test-storage.
         */
        @JvmStatic
        actual fun getTestStorage(): PgStorage = PsqlTestStorage.getTestOrInitStorage()

        /**
         * Create a new test-storage to execute tests.
         * @return the test-storage.
         */
        @JvmStatic
        actual fun newTestStorage(): PgStorage = PsqlTestStorage.newTestStorage()

        /**
         * Decode a GeoJSON geometry from encoded bytes.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        actual fun decodeGeometry(bytes: ByteArray?, flags: Int): SpGeometry? {
            TODO("Not yet implemented")
        }

        /**
         * Encodes the given GeoJSON geometry into bytes.
         * @param geometry the geometry to encode.
         * @param flags the codec flags.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        actual fun encodeGeometry(geometry: SpGeometry?, flags: Int): ByteArray {
            TODO("Not yet implemented")
        }
    }
}