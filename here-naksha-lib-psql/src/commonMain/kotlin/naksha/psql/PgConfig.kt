@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.PAnyMap
import naksha.base.FeatureType
import naksha.base.Id
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.objects.NakshaProperties
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
    override fun withCreate(create: Boolean): PgConfig = super.withCreate(create) as PgConfig
    override fun withClassName(className: String): PgConfig = super.withClassName(className) as PgConfig
    override fun withUpgrade(upgrade: Boolean): PgConfig = super.withUpgrade(upgrade) as PgConfig
    override fun withId(value: Id?): PgConfig = super.withId(value) as PgConfig
    override fun withType(value: String?): PgConfig = super.withType(value) as PgConfig
    override fun withFeatureType(value: FeatureType?): PgConfig = super.withFeatureType(value) as PgConfig
    override fun withBbox(value: SpBoundingBox?): PgConfig = super.withBbox(value) as PgConfig
    override fun withGeometry(value: SpGeometry?): PgConfig = super.withGeometry(value) as PgConfig
    override fun withReferencePoint(value: SpPoint?): PgConfig = super.withReferencePoint(value) as PgConfig
    override fun withProperties(value: NakshaProperties): PgConfig = super.withProperties(value) as PgConfig
    override fun withMomType(value: String?): PgConfig = super.withMomType(value) as PgConfig

    /**
     * Create a default PostgresQL configuration.
     * @since 3.0
     */
    @JsName("of")
    constructor(id: Id): this() {
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
    var master: PgInstanceConfig by MASTER

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
        // https://github.com/heremaps/naksha/blob/v2/here-naksha-app-service/src/jvmTest/resources/unit_test_data/StorageApi/TC0001_createStorage/create_storage.json
        val rawProperties = getRaw("properties")
        val rawDbConfig = if (rawProperties is PAnyMap) rawProperties.getRaw("dbConfig") else null
        return if (rawDbConfig is PAnyMap) rawDbConfig.proxy(PgInstanceConfig::class) else null
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
                if (replica != null) list.add(replica.withReadOnly(true).toString())
            }
            return list
        }

    /**
     * Can be set to _true_ to force the storage to reinstall the admin-map, even when the existing installed version of Naksha code is up-to-date (matches the code coming together with the library).
     * @since 3.0
     */
    var override by BOOLEAN_FALSE

    /**
     * Special parameter to force `lib-psql` to install the admin-map in this [version][naksha.model.NakshaVersion]. This is only for debugging purpose, and should not be used in any productive environment, normally the correct version is set, which is [adminVersion].
     *
     * **Warning**: This does not change the actual code that is installed, which will be always what is in the resources of the library, rather it modifies the version number that this code stores, so that the next time an upgrade will be executed. This option is really for debugging purpose only, use with care!
     * @since 3.0
     */
    var version by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [temporary][PgStorageClass.Temporary] collections, if they should be stored in a special tablespace.
     * @since 3.0
     */
    var temp_tablespace by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [brittle][PgStorageClass.Brittle] collections, if they should be stored in a special tablespace.
     * @since 3.0
     */
    var brittle_tablespace by STRING_NULL

    /**
     * Change the name of the tablespace in which to store [ephemeral][PgStorageClass.Ephemeral] collections, if they should be stored in a special tablespace.
     * @since 3.0
     */
    var ephemeral_tablespace by STRING_NULL

    override fun configEquals(other: NakshaStorage): Boolean {
        val otherConfig = other.proxy(this::class)
        return hardCap == otherConfig.hardCap &&
                create == otherConfig.create &&
                upgrade == otherConfig.upgrade &&
                version == otherConfig.version &&
                pgInstancesEquals(master, otherConfig.master) &&
                replicasEquals(replicas, otherConfig.replicas)
    }

    private fun pgInstancesEquals(i1: PgInstanceConfig, i2: PgInstanceConfig): Boolean {
        return i1.host == i2.host &&
                i1.port == i2.port &&
                i1.db == i2.db &&
                i1.user == i2.user &&
                i1.password == i2.password &&
                i1.readOnly == i2.readOnly &&
                i1.connectionLimit == i2.connectionLimit
    }

    private fun replicasEquals(l1: PgInstanceConfigList, l2: PgInstanceConfigList): Boolean {
        if(l1.size != l2.size) return false
        val l1Sorted = l1.sortedBy { it.toString() }
        val l2Sorted = l2.sortedBy { it.toString() }
        return l1Sorted
            .zip(l2Sorted)
            .all { (i1, i2) ->
                (i1 == null && i2 == null) || (i1 != null && i2 != null && pgInstancesEquals(i1, i2))
            }
    }
}
