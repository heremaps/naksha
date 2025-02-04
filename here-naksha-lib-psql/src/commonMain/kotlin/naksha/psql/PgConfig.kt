@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.StorageConfig
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The PostgresQL storage configuration.
 * @since 3.0.0
 */
@Suppress("unused")
@JsExport
class PgConfig() : StorageConfig() {

    override fun defaultClassName(): String = "naksha.psql.PsqlStorage"

    /**
     * Create a default PostgresQL configuration.
     * @since 3.0.0
     */
    @JsName("of")
    constructor(id: String): this() {
        this.id = id
        this.className = defaultClassName()
    }

    companion object PsqlConfig_C {
        private val MASTER = NotNullProperty<PgConfig, PgInstanceConfig>(PgInstanceConfig::class) { self, _ ->
            // If null, try to port old code, so try properties.dbConfig
            // https://github.com/heremaps/naksha/blob/v2/here-naksha-app-service/src/test/resources/unit_test_data/StorageApi/TC0001_createStorage/create_storage.json
            val raw = self.getRaw("properties")
            val dbConfig =if (raw is AnyObject) raw.getRaw("dbConfig") else null
            if (dbConfig is AnyObject) dbConfig.proxy(PgInstanceConfig::class) else throw NakshaException(ILLEGAL_STATE, "Missing master configuration")
        }
        private val REPLICAS_LIST = NotNullProperty<PgConfig, PgInstanceConfigList>(PgInstanceConfigList::class) {
             _,_ -> PgInstanceConfigList()
        }
        private val BOOLEAN_FALSE = NullableProperty<PgConfig, Boolean>(Boolean::class) { _, _ -> false }
        private val STRING_NULL = NullableProperty<PgConfig, String>(String::class)
    }

    /**
     * The PostgresQL master to connect to.
     * @since 3.0.0
     */
    var master by MASTER

    /**
     * Sets the [master] and returns this.
     * @param master the master to set.
     * @return this.
     */
    fun withMaster(master: PgInstanceConfig): PgConfig {
        this.master = master
        return this
    }

    /**
     * The [JDBC connection string](https://jdbc.postgresql.org/documentation/use/) of the PostgresQL master server, for example `jdbc:postgresql://localhost:5432/testdb?user=fred&password=secret&ssl=true`, created from the [master] configuration.
     * @since 3.0.0
     */
    val masterUri: String
        get() = "jdbc:postgresql://${master.host}:${master.port}/${master.db}?user=${master.user}&password=${master.password}&ssl=true"

    /**
     * The list of PostgresQL replicas.
     * @since 3.0.0
     */
    var replicas by REPLICAS_LIST

    /**
     * An optional list of [JDBC connection strings](https://jdbc.postgresql.org/documentation/use/) of the PostgresQL replication servers, like `jdbc:postgresql://localhost:5432/testdb?user=fred&password=secret&ssl=true`, created from the [replicas] configuration.
     * @since 3.0.0
     */
    val replicaUris: StringList
        get() {
            val list = StringList()
            val replicas = this.replicas
            for (replica in replicas) {
                if (replica != null) list.add("jdbc:postgresql://${replica.host}:${replica.port}/${replica.db}?user=${replica.user}&password=${replica.password}&ssl=true")
            }
            return list
        }

    /**
     * Can be set to _true_ to force the storage to reinstall the admin-map, even when the existing installed version of Naksha code is up-to-date (matches the code coming together with the library).
     */
    val override by BOOLEAN_FALSE

    /**
     * Special parameter to force `lib-psql` to install the admin-map in this [version][naksha.model.NakshaVersion]. This is only for debugging purpose, and should not be used in any productive environment, normally the correct version is set, which is [latest][naksha.model.NakshaVersion.latest].
     *
     * **Warning**: This does not change the actual code that is installed, which will be always what is in the resources of the library, rather it modifies the version number that this code stores, so that the next time an upgrade will be executed. This option is really for debugging purpose only, use with care!
     */
    val version by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [temporary][PgStorageClass.Temporary] collections, if they should be stored in a special tablespace.
     */
    val temp_tablespace by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [brittle][PgStorageClass.Brittle] collections, if they should be stored in a special tablespace.
     */
    val brittle_tablespace by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [ephemeral][PgStorageClass.Ephemeral] collections, if they should be stored in a special tablespace.
     */
    val ephemeral_tablespace by STRING_NULL
}
