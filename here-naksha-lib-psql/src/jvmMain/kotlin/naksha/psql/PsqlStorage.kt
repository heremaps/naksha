package naksha.psql

import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED

/**
 * The Java implementation of the [IStorage] interface.
 *
 * The `PsqlStorage` class is extended in `here-naksha-storage-psql`, which has a `PsqlStoragePlugin` class that implements the `Plugin` contract (internal contract in _Naksha-Hub_), and makes the storage available to the **Naksha-Hub** as a storage plugin. It parses the configuration from feature properties given to the plugin-constructor, and then creates the [PsqlCluster], eventually calling this constructor.
 *
 * The Java version runs a background job to get notifications of database changes.
 *
 * @constructor Creates a new PSQL storage.
 * @property cluster the PostgresQL cluster used by this storage.
 * @param defaultSchemaName the default schema name.
 */
open class PsqlStorage(override val cluster: PsqlCluster, defaultSchemaName: String) : PgStorage(cluster, defaultSchemaName), IStorage {

    private var _channel: String? = null

    /**
     * The name of the notification channel used.
     *
     * - Will throw [NakshaError.UNINITIALIZED] if read before [initStorage].
     * - Will throw [NakshaError.ILLEGAL_STATE] if change after [initStorage]
     */
    var channel: String
        get() = _channel ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")
        set(value) {
            if (!isInitialized()) throw NakshaException(ILLEGAL_STATE, "Storage already initialized")
            _channel = value
        }
    private lateinit var listener: PsqlStorageListener

    override fun newMap(storage: PgStorage, mapId: String): PsqlMap = PsqlMap(storage, mapId, mapIdToSchema(mapId))

    override fun initStorage(params: Map<String, *>?) {
        super.initStorage(params)
        if (_channel == null) _channel = "lib-psql-${id()}"
        if (!this::listener.isInitialized) listener = PsqlStorageListener(this)
    }
    override fun newSession(options: SessionOptions, readOnly: Boolean): PsqlSession = PsqlSession(this, options, readOnly)
    override fun defaultMap(): PsqlMap = super.defaultMap() as PsqlMap
    override operator fun get(mapId: String): PsqlMap = super.get(mapId) as PsqlMap
}