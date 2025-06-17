@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_ARGUMENT
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The configuration of a single PostgresQL instance.
 * @since 3.0
 */
@Suppress("unused")
@JsExport
class PgInstanceConfig : AnyObject() {
    companion object PgInstanceConfig_C {
        /**
         * The [PlatformType] of [PgInstanceConfig].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgInstanceConfig::class).withPackageName(PACKAGE_NAME)

        internal const val DEFAULT_DB = "postgres"
        internal const val DEFAULT_USER = "postgres"
        internal const val DEFAULT_PASSWORD = "password"
        internal const val DEFAULT_PORT = 5432
        private val HOST = NotNullProperty<PgInstanceConfig, String>(String_TYPE) { _,_ -> "localhost" }
        private val PORT = NotNullProperty<PgInstanceConfig, Int>(Int_TYPE) { _, _ -> DEFAULT_PORT }
        private val DATABASE = NotNullProperty<PgInstanceConfig, String>(String_TYPE) { _, _ -> DEFAULT_DB }
        private val USER = NotNullProperty<PgInstanceConfig, String>(String_TYPE) { _, _ -> DEFAULT_USER }
        private val PASSWORD = NotNullProperty<PgInstanceConfig, String>(String_TYPE) { _, _ -> DEFAULT_PASSWORD }
        private val READ_ONLY = NotNullProperty<PgInstanceConfig, Boolean>(Boolean_TYPE) { _, _ -> false }
        private val CONNECTION_LIMIT = NotNullProperty<PgInstanceConfig, Int>(Int_TYPE) { self, _ ->
            // Compatibility hack, see:
            // https://github.com/heremaps/naksha/blob/v2/here-naksha-app-service/src/jvmTest/resources/unit_test_data/StorageApi/TC0001_createStorage/create_storage.json
            val raw = self.getRaw("maxPoolSize")
            if (raw is Number) raw.toInt() else 1024
        }
        private const val EXPECTED_URI_FORMAT = "jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}"

        /**
         * Parses the given URI into a [PgInstanceConfig], the URI should look like: `jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}`
         * - Throws [ILLEGAL_ARGUMENT] if the given string is not well formatted.
         * @param uri the URI to parse.
         * @return the configuration generated form it.
         * @since 3.0
         */
        fun fromUri(uri: String): PgInstanceConfig {
            val questionMarkIndex = uri.indexOf("?")
            if (questionMarkIndex < 0) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Missing query parameters, URI should be like: $EXPECTED_URI_FORMAT")
            }
            val query = uri.substring(questionMarkIndex+1, uri.length)
                .split("&")
                .map { it.split("=", limit = 2) }
                .associate { if (it.size==2) it[0] to it[1] else it[0] to null }

            if (!uri.startsWith("jdbc:postgresql://")) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Wrong URI prefix, URI should be like: $EXPECTED_URI_FORMAT")
            }
            val user = query["user"] ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing 'user', URI should be like: $EXPECTED_URI_FORMAT")
            val password = query["password"] ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing 'password', URI should be like: $EXPECTED_URI_FORMAT")
            val ro = query["readOnly"]
            val isWriter = ro == null || "false".equals(ro, true)
            val urlParts = uri.substring("jdbc:postgresql://".length, questionMarkIndex).split(':', '/')
            if(urlParts.size != 2 && urlParts.size != 3) {
                throw NakshaException(ILLEGAL_ARGUMENT, "The database URI is not well formatted, should be: $EXPECTED_URI_FORMAT")
            }
            val host: String = urlParts[0]
            val port: Int
            try {
                port = if (urlParts.size == 2) 5432 else urlParts[1].toInt()
            } catch (e: NumberFormatException) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Illegal port number '${urlParts[1]}', URI should be like: $EXPECTED_URI_FORMAT")
            }
            val database: String = if (urlParts.size == 2) urlParts[1] else urlParts[2]
            return PgInstanceConfig()
                .withDb(database)
                .withHost(host).withPort(port)
                .withUser(user).withPassword(password)
                .withReadOnly(!isWriter)
        }
    }

    /**
     * The host to connect to.
     * @since 3.0
     */
    var host by HOST

    /**
     * Set the host.
     * @param host the host.
     * @return this.
     * @since 3.0
     */
    fun withHost(host: String): PgInstanceConfig {
        this.host = host
        return this
    }

    /**
     * The port to connect to, defaults to `5432`.
     * @since 3.0
     */
    var port by PORT

    /**
     * Set the port.
     * @param port the port.
     * @return this.
     * @since 3.0
     */
    fun withPort(port: Int): PgInstanceConfig {
        this.port = port
        return this
    }

    /**
     * The database to open.
     * @since 3.0
     */
    var db by DATABASE

    /**
     * Set the database.
     * @param db the database.
     * @return this.
     * @since 3.0
     */
    fun withDb(db: String): PgInstanceConfig {
        this.db = db
        return this
    }

    /**
     * The user to authenticate with.
     * @since 3.0
     */
    var user by USER

    /**
     * Set the user.
     * @param user the user.
     * @return this.
     * @since 3.0
     */
    fun withUser(user: String): PgInstanceConfig {
        this.user = user
        return this
    }

    /**
     * The password to authenticate with.
     * @since 3.0
     */
    var password by PASSWORD

    /**
     * Set the password.
     * @param password the password.
     * @return this.
     * @since 3.0
     */
    fun withPassword(password: String): PgInstanceConfig {
        this.password = password
        return this
    }

    /**
     * If the instance is read-only (replica).
     * @since 3.0
     */
    var readOnly by READ_ONLY

    /**
     * Set the read-only attribute.
     * @param readOnly if the instance is read-only (replica).
     * @return this.
     * @since 3.0
     */
    fun withReadOnly(readOnly: Boolean): PgInstanceConfig {
        this.readOnly = readOnly
        return this
    }

    /**
     * The maximum amount of connections this instance can handle.
     *
     * All master instances must at least support one admin-connection, additionally to this limit.
     * @since 3.0
     */
    var connectionLimit by CONNECTION_LIMIT

    /**
     * Set the connection-limit.
     * @param connectionLimit the connection-limit.
     * @return this.
     * @since 3.0
     */
    fun withConnectionLimit(connectionLimit: Int): PgInstanceConfig {
        this.connectionLimit = connectionLimit
        return this
    }

    /**
     * Returns the standard JDBC URI, basically `jdbc:postgresql://{host}:{port}/{db}?user={user}&password={password}&ssl=true[&readOnly]`.
     * @return the JDBC URI.
     */
    override fun toString(): String
        = "jdbc:postgresql://${host}:${port}/${db}?user=${user}&password=${password}&ssl=true${if (readOnly) "&readOnly" else ""}"
}