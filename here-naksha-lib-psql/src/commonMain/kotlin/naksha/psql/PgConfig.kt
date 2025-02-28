@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.objects.NakshaStorage
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The PostgresQL storage configuration.
 * @since 3.0
 */
@Suppress("unused")
@JsExport
class PgConfig() : NakshaStorage() {

    override fun defaultClassName(): String = "naksha.psql.PsqlStorage"

    /**
     * Create a default PostgresQL configuration.
     * @since 3.0
     */
    @JsName("of")
    constructor(id: String): this() {
        this.id = id
        this.className = defaultClassName()
    }

    companion object PsqlConfig_C {
        private val MASTER = NotNullProperty<PgConfig, PgInstanceConfig>(PgInstanceConfig::class) { self, _ ->
            self.getMasterOrNull() ?: throw NakshaException(ILLEGAL_STATE, "master not found")
        }
        private val REPLICAS_LIST = NotNullProperty<PgConfig, PgInstanceConfigList>(PgInstanceConfigList::class) { _,_ ->
            PgInstanceConfigList() }
        private val BOOLEAN_FALSE = NullableProperty<PgConfig, Boolean>(Boolean::class) { _, _ -> false }
        private val STRING_NULL = NullableProperty<PgConfig, String>(String::class)
    }

    /**
     * The PostgresQL master to connect to.
     * @since 3.0
     */
    var master by MASTER

    /**
     * Tests if the configuration has an explicit master setting.
     * @return the master configuration, if any is explicitly set.
     * @since 3.0
     */
    fun getMasterOrNull(): PgInstanceConfig? {
        // Try if master is a string
        val raw = getRaw("master")
        if (raw is String) {
            try {
                return PgInstanceConfig.fromUri(raw)
            } catch (_: NakshaException) {}
        }
        // Try to port old code, so try properties.dbConfig
        // https://github.com/heremaps/naksha/blob/v2/here-naksha-app-service/src/test/resources/unit_test_data/StorageApi/TC0001_createStorage/create_storage.json
        val rawProperties = getRaw("properties")
        val rawDbConfig = if (rawProperties is AnyObject) rawProperties.getRaw("dbConfig") else null
        return if (rawDbConfig is AnyObject) rawDbConfig.proxy(PgInstanceConfig::class) else null
    }

    /**
     * Sets the [master] and returns this.
     * @param master the master to set.
     * @return this.
     * @since 3.0
     */
    fun withMaster(master: PgInstanceConfig): PgConfig {
        this.master = master
        return this
    }

    /**
     * The [JDBC connection string](https://jdbc.postgresql.org/documentation/use/) of the PostgresQL master server, for example `jdbc:postgresql://localhost:5432/testdb?user=fred&password=secret&ssl=true`, created from the [master] configuration.
     * @since 3.0
     */
    val masterUri: String
        get() = master.toString()

    /**
     * Sets the [master] and returns this.
     * @param uri the master-URI to set, the formatted like `jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}`.
     * @return this.
     * @since 3.0
     */
    fun withMasterUri(uri: String): PgConfig {
        this.master = PgInstanceConfig.fromUri(uri)
        return this
    }

    /**
     * The list of PostgresQL replicas.
     * @since 3.0
     */
    var replicas by REPLICAS_LIST

    /**
     * An optional list of [JDBC connection strings](https://jdbc.postgresql.org/documentation/use/) of the PostgresQL replication servers, like `jdbc:postgresql://localhost:5432/testdb?user=fred&password=secret&ssl=true`, created from the [replicas] configuration.
     * @since 3.0
     */
    val replicaUris: StringList
        get() {
            val list = StringList()
            val replicas = this.replicas
            for (replica in replicas) {
                if (replica != null) list.add(replica.toString())
            }
            return list
        }

    /**
     * Can be set to _true_ to force the storage to reinstall the admin-map, even when the existing installed version of Naksha code is up-to-date (matches the code coming together with the library).
     * @since 3.0
     */
    val override by BOOLEAN_FALSE

    /**
     * Special parameter to force `lib-psql` to install the admin-map in this [version][naksha.model.NakshaVersion]. This is only for debugging purpose, and should not be used in any productive environment, normally the correct version is set, which is [latest][naksha.model.NakshaVersion.latest].
     *
     * **Warning**: This does not change the actual code that is installed, which will be always what is in the resources of the library, rather it modifies the version number that this code stores, so that the next time an upgrade will be executed. This option is really for debugging purpose only, use with care!
     * @since 3.0
     */
    val version by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [temporary][PgStorageClass.Temporary] collections, if they should be stored in a special tablespace.
     * @since 3.0
     */
    val temp_tablespace by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [brittle][PgStorageClass.Brittle] collections, if they should be stored in a special tablespace.
     * @since 3.0
     */
    val brittle_tablespace by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [ephemeral][PgStorageClass.Ephemeral] collections, if they should be stored in a special tablespace.
     * @since 3.0
     */
    val ephemeral_tablespace by STRING_NULL
}
