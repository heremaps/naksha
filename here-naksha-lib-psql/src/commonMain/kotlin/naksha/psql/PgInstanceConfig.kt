@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The configuration of a single PostgresQL instance.
 * @since 3.0.0
 */
@Suppress("unused")
@JsExport
class PgInstanceConfig : AnyObject() {
    companion object PgInstanceConfig_C {
        private val STRING = NotNullProperty<PgInstanceConfig, String>(String::class)
        private val PORT = NotNullProperty<PgInstanceConfig, Int>(Int::class) { _,_ -> 5432 }
        private val CONNECTION_LIMIT = NotNullProperty<PgInstanceConfig, Int>(Int::class) { self,_ ->
            // Compatibility hack, see:
            // https://github.com/heremaps/naksha/blob/v2/here-naksha-app-service/src/test/resources/unit_test_data/StorageApi/TC0001_createStorage/create_storage.json
            val raw = self.getRaw("maxPoolSize")
            if (raw is Number) raw.toInt() else 1024
        }
    }

    /**
     * The host to connect to.
     * @since 3.0.0
     */
    var host by STRING

    /**
     * Set the host.
     * @param host the host.
     * @return this.
     * @since 3.0.0
     */
    fun withHost(host: String): PgInstanceConfig {
        this.host = host
        return this
    }

    /**
     * The port to connect to, defaults to `5432`.
     * @since 3.0.0
     */
    var port by PORT

    /**
     * Set the port.
     * @param port the port.
     * @return this.
     * @since 3.0.0
     */
    fun withPort(port: Int): PgInstanceConfig {
        this.port = port
        return this
    }

    /**
     * The database to open.
     * @since 3.0.0
     */
    var db by STRING

    /**
     * Set the database.
     * @param db the database.
     * @return this.
     * @since 3.0.0
     */
    fun withDb(db: String): PgInstanceConfig {
        this.db = db
        return this
    }

    /**
     * The user to authenticate with.
     * @since 3.0.0
     */
    var user by STRING

    /**
     * Set the user.
     * @param user the user.
     * @return this.
     * @since 3.0.0
     */
    fun withUser(user: String): PgInstanceConfig {
        this.user = user
        return this
    }

    /**
     * The password to authenticate with.
     * @since 3.0.0
     */
    var password by STRING

    /**
     * Set the password.
     * @param password the password.
     * @return this.
     * @since 3.0.0
     */
    fun withPassword(password: String): PgInstanceConfig {
        this.password = password
        return this
    }

    /**
     * The maximum amount of connections this instance can handle.
     *
     * All master instances must at least support one admin-connection, additionally to this limit.
     * @since 3.0.0
     */
    var connectionLimit by CONNECTION_LIMIT

    /**
     * Set the connection-limit.
     * @param connectionLimit the connection-limit.
     * @return this.
     * @since 3.0.0
     */
    fun withConnectionLimit(connectionLimit: Int): PgInstanceConfig {
        this.connectionLimit = connectionLimit
        return this
    }
}