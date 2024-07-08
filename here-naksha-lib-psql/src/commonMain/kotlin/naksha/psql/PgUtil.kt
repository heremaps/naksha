package naksha.psql

import naksha.model.NakshaContext

/**
 * PostgresQL
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PgUtil {
    companion object {
        /**
         * Given as parameter for [PgStorage.initStorage], `options` can be a [PgOptions] object to be used for the initialization
         * connection (specific changed defaults to timeouts and locks).
         */
        val OPTIONS: String

        /**
         * Given as parameter for [PgStorage.initStorage], `context` can be a [NakshaContext] to be used while doing the initialization;
         * only if [superuser][NakshaContext.su] is _true_, then a not uninitialized storage is installed. This requires as well
         * superuser rights in the PostgresQL database.
         */
        val CONTEXT: String

        /**
         * Given as parameter for [PgStorage.initStorage], `id` used if the storage is uninitialized, initialize it with the given
         * storage identifier. If the storage is already initialized, reads the existing identifier and compares it with the given one.
         * If they do not match, throws an [IllegalStateException]. If not given a random new identifier is generated, when no identifier
         * yet exists. It is strongly recommended to provide the identifier.
         */
        val ID: String

        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts The literal parts to merge and quote.
         * @return The quoted literal.
         */
        fun quoteLiteral(vararg parts: String): String

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         */
        fun quoteIdent(vararg parts: String): String

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
        fun getInstance(host: String, port: Int = 5432, database: String, user: String, password: String, readOnly: Boolean = false): PgInstance

        /**
         * Returns the instance for the given JDBC URL.
         * @param url the JDBC URL, for example `jdbc:postgresql://foo.com/bar_db?user=postgres&password=password`
         * @throws UnsupportedOperationException if executed in `PLV8` extension.
         */
        fun getInstance(url: String): PgInstance

        /**
         * Creates a new cluster configuration.
         * @param master the master PostgresQL server.
         * @param replicas the read-replicas; if any.
         * @throws UnsupportedOperationException if executed in `PLV8` extension.
         */
        fun newCluster(master: PgInstance, vararg replicas: PgInstance): PgCluster

        /**
         * Creates a new PostgresQL storage engine.
         * @param cluster the PostgresQL server cluster to use.
         * @param options the default options when opening new connections.
         */
        fun newStorage(cluster: PgCluster, options: PgOptions): PgStorage

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        fun isPlv8(): Boolean

        /**
         * Returns the [PLV8 extension](https://plv8.github.io/) storage.
         * @return the [PLV8 extension](https://plv8.github.io/) storage; _null_ if this code is not executed within PostgresQL database.
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
        fun initTestStorage(defaultOptions: PgOptions, params: Map<String, *>? = null): Boolean

        /**
         * Returns the existing test-storage to execute tests. If no test storage exists yet, creates a new test storage.
         * @return the test-storage.
         * @throws UnsupportedOperationException if this platform does not support running tests.
         */
        fun getTestStorage(): PgStorage
    }
}