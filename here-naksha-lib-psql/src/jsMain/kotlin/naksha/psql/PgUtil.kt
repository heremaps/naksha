package naksha.psql

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")
@JsExport
actual object PgUtil {
    private fun plv8Forbidden(opName: String) {
        if (isPlv8()) throw UnsupportedOperationException("${opName}: Not supported in PLV8 storage")
    }

    /**
     * Given as parameter for [PgStorage.initStorage], `override` can be set to _true_ to force the storage to reinstall, even when
     * the existing installed version of Naksha code is up-to-date.
     */
    actual val OVERRIDE: String = "override"

    /**
     * Given as parameter for [PgStorage.initStorage], `options` can be a [PgOptions] object to be used for the initialization
     * connection (specific changed defaults to timeouts and locks).
     */
    actual val OPTIONS: String = "options"

    /**
     * Given as parameter for [PgStorage.initStorage], `context` can be a [naksha.model.NakshaContext] to be used while doing the
     * initialization; only if [superuser][naksha.model.NakshaContext.su] is _true_, then a not uninitialized storage is installed.
     * This requires as well superuser rights in the PostgresQL database.
     */
    actual val CONTEXT: String = "context"

    /**
     * Given as parameter for [PgStorage.initStorage], `id` used if the storage is uninitialized, initialize it with the given
     * storage identifier. If the storage is already initialized, reads the existing identifier and compares it with the given one.
     * If they do not match, throws an [IllegalStateException]. If not given a random new identifier is generated, when no identifier
     * yet exists. It is strongly recommended to provide the identifier.
     */
    actual val ID: String = "id"

    /**
     * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
     * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
     * @param parts the literal parts to merge and quote.
     * @return The quoted literal.
     */
    actual fun quoteLiteral(vararg parts: String): String {
        if (isPlv8()) return js("parts?plv8.quote_literal(parts.join('')):''").unsafeCast<String>()
        return PgStatic.quote_literal(*parts)
    }

    /**
     * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
     * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
     */
    actual fun quoteIdent(vararg parts: String): String {
        if (isPlv8()) return js("parts?plv8.quote_ident(parts.join('')):''").unsafeCast<String>()
        return PgStatic.quote_ident(*parts)
    }

    /**
     * Calculates the partition number between 0 and 255. This is the unsigned value of the first byte of the MD5 hash above the
     * given feature-id. When there are less than 256 partitions, the value must be divided by the number of partitions and the rest
     * addresses the partition, for example for 4 partitions we get `partitionNumber(id) % 4`, what will be a value between 0 and 3.
     * In PVL8 this is implemented using the native code as `get_byte(digest(id,'md5'),0)`, which is as well what the partitioning
     * statement will do.
     * @param featureId the feature id.
     * @return the partition number of the feature, a value between 0 and 255.
     */
    actual fun partitionNumber(featureId: String): Int {
        if (isPlv8()) {
            return js("plv8.execute(\"SELECT get_byte(digest(\$1,'md5'),0) as i\",[featureId])[0].i").unsafeCast<Int>()
        }
        throw UnsupportedOperationException("PgUtil::partitionNumber is not implemented in the browser yet")
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
     * @throws UnsupportedOperationException if executed in `PLV8` extension.
     */
    actual fun getInstance(
        host: String,
        port: Int,
        database: String,
        user: String,
        password: String,
        readOnly: Boolean
    ): PgInstance {
        plv8Forbidden("PgUtil.getInstance")
        TODO("Not yet implemented")
    }

    /**
     * Returns the instance for the given JDBC URL.
     * @param url the JDBC URL, for example `jdbc:postgresql://foo.com/bar_db?user=postgres&password=password`
     * @throws UnsupportedOperationException if executed in `PLV8` extension.
     */
    @JsName("getInstanceFromJdbcUrl")
    actual fun getInstance(url: String): PgInstance {
        plv8Forbidden("PgUtil.getInstance")
        TODO("Not yet implemented")
    }

    /**
     * Creates a new cluster configuration.
     * @param master the master PostgresQL server.
     * @param replicas the read-replicas; if any.
     * @throws UnsupportedOperationException if executed in `PLV8` extension.
     */
    actual fun newCluster(master: PgInstance, vararg replicas: PgInstance): PgCluster {
        plv8Forbidden("PgUtil.newCluster")
        TODO("Not yet implemented")
    }

    /**
     * Creates a new PostgresQL storage engine.
     * @param cluster the PostgresQL server cluster to use.
     * @param options the default options when opening new connections.
     */
    actual fun newStorage(cluster: PgCluster, options: PgOptions): PgStorage {
        plv8Forbidden("PgUtil.newStorage")
        TODO("Not yet implemented")
        // Should return the NodeJsStorage!
    }

    /**
     * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
     * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
     */
    actual fun isPlv8(): Boolean = js("typeof plv8==='object'").unsafeCast<Boolean>()

    /**
     * Returns the [PLV8 extension](https://plv8.github.io/) storage.
     * @return the [PLV8 extension](https://plv8.github.io/) storage; _null_ if this code is not executed within PostgresQL database.
     * @throws UnsupportedOperationException if called, when [isPlv8] returns _false_.
     */
    actual fun getPlv8(): PgStorage {
        if (!isPlv8()) throw UnsupportedOperationException("PgUtil.getPlv8: Only supported in PLV8 storage")
        TODO("Create a new virtual storage instance and add to plv8, if not done already")
        // Note: The session is opened using the new(Read|Write)Connection call.
        //       The storage does not have a cluster
        //       We need to implement a Plv8Storage and a NodeJsStorage
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
     * @throws UnsupportedOperationException if this platform does not support running tests.
     */
    actual fun initTestStorage(defaultOptions: PgOptions, params: Map<String, *>?): Boolean {
        plv8Forbidden("PgUtil.getTestStorage")
        // TODO: Can we fix this for JavaScript/TypeScript?
        throw UnsupportedOperationException("Testing not supported in PLV8")
    }

    /**
     * Returns the existing test-storage to execute tests. If no test storage exists yet, creates a new test storage.
     * @return the test-storage.
     * @throws UnsupportedOperationException if this platform does not support running tests.
     */
    actual fun getTestStorage(): PgStorage {
        plv8Forbidden("PgUtil.getTestStorage")
        // TODO: Can we fix this for JavaScript/TypeScript?
        throw UnsupportedOperationException("Testing not supported in PLV8")
    }
}