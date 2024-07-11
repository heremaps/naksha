package naksha.psql

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PgUtil {
    actual companion object {
        /**
         * Given as parameter for [PgStorage.initStorage], `override` can be set to _true_ to force the storage to reinstall, even when
         * the existing installed version of Naksha code is up-to-date.
         */
        @JvmField
        actual val OVERRIDE: String = "override"

        /**
         * Given as parameter for [PgStorage.initStorage], `options` can be a [PgOptions] object to be used for the initialization
         * connection (specific changed defaults to timeouts and locks).
         */
        @JvmField
        actual val OPTIONS: String = "options"

        /**
         * Given as parameter for [PgStorage.initStorage], `context` can be a [naksha.model.NakshaContext] to be used while doing the
         * initialization; only if [superuser][naksha.model.NakshaContext.su] is _true_, then a not uninitialized storage is installed.
         * This requires as well superuser rights in the PostgresQL database.
         */
        @JvmField
        actual val CONTEXT: String = "context"

        /**
         * Given as parameter for [PgStorage.initStorage], `id` used if the storage is uninitialized, initialize it with the given
         * storage identifier. If the storage is already initialized, reads the existing identifier and compares it with the given one.
         * If they do not match, throws an [IllegalStateException]. If not given a random new identifier is generated, when no identifier
         * yet exists. It is strongly recommended to provide the identifier.
         */
        @JvmField
        actual val ID: String = "id"

        /**
         * Special parameter only for JVM storage to install the needed database SQL code in this version. The value is expected to be a
         * [naksha.model.NakshaVersion].
         */
        @JvmField
        val VERSION: String = "version"

        /**
         * A parameter that can be given to [getTestStorage] to not start a docker container, but to connect the test storage against an
         * existing database with this URL. This parameter is as well auto-detect from the environment variable named
         * `NAKSHA_TEST_PSQL_DB_URL`.
         */
        @JvmField
        var TEST_URL = "test_url"

        /**
         * A parameter that can be given to [getTestStorage] to not start a docker container, but to connect against the given instance.
         */
        @JvmField
        var TEST_INSTANCE = "test_instance"

        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts The literal parts to merge and quote.
         * @return The quoted literal.
         */
        @JvmStatic
        actual fun quoteLiteral(vararg parts: String): String = PgStatic.quote_literal(*parts)

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         */
        @JvmStatic
        actual fun quoteIdent(vararg parts: String): String = PgStatic.quote_ident(*parts)

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

        /**
         * Creates a new PostgresQL storage engine.
         * @param cluster the PostgresQL server cluster to use.
         * @param options the default options when opening new connections.
         */
        @JvmStatic
        actual fun newStorage(cluster: PgCluster, options: PgOptions): PgStorage {
            require(cluster is PsqlCluster) { "The Java PSQL storage only works with PsqlCluster instances, please use PgUtil.newCluster" }
            return PsqlStorage(cluster, options)
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
        actual fun initTestStorage(defaultOptions: PgOptions, params: Map<String, *>?): Boolean {
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
         * Returns the existing test-storage to execute tests. If no test storage exists yet, creates a new test storage. This is an
         * alias for [getTestStorage], but cast to correct type.
         * @return the test-storage.
         */
        @JvmStatic
        fun psqlTestStorage(): PsqlTestStorage = getTestStorage() as PsqlTestStorage
    }
}