package naksha.psql

import naksha.base.Int64
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.FORBIDDEN
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED

/**
 * The Java implementation of the [PgStorage].
 *
 * @constructor Creates a new PSQL storage.
 * @property cluster the PostgresQL cluster used by this storage.
 * @param defaultSchemaName the default schema name.
 */
class JvmPgStorage : PgStorage(), IStorage {

    private var _channel: String? = null

    /**
     * The name of the notification channel used.
     *
     * - Will throw [UNINITIALIZED] if read before [initStorage].
     * - Will throw [ILLEGAL_STATE] if change after [initStorage]
     */
    var channel: String
        get() = _channel ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")
        private set(value) {
            _channel = value
        }
    private lateinit var listener: PsqlStorageListener

    /**
     * Initializes the storage.
     *
     * For the [JvmPgStorage] implementation, this will test if an [administration-map][Naksha.ADMIN_MAP] exists (schema `naksha~admin`), and if the _storage-id_ and _storage-number_ match the existing installation. If no such map exists, this method will create one, and install all extensions, create all functions, setup needed sequences, and install the _storage-id_ and _storage-number_, so that the next time the storage is opened, it can be verified. It will as well remember which version of Naksha is installed to be able to upgrade the functions on demand, and the base administration collections will be created, which are: [Naksha.TRANSACTIONS_COL], [Naksha.MAPS_COL], and [Naksha.DICTIONARIES_COL].
     *
     * This operation requires that the current [context][NakshaContext] has the [superuser][NakshaContext.su] rights.
     *
     * This method will register the storage with the [NakshaCache].
     *
     * - Throws [FORBIDDEN], if not called as super-user.
     * - Throws [NakshaError.STORAGE_ID_MISMATCH], if the given [id] and/or [number] do not match an existing one.
     * @param id the identifier of the storage (_added in v3.0.0_).
     * @param number the number of the storage (_added in v3.0.0_).
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @since 2.0.8
     */
    override fun initStorage(id: String, number: Int64, params: Map<String, *>?) {
        try {
            super.initStorage(id, number, params)
        } catch (e: NakshaException) {
            if (e.code != UNINITIALIZED) throw e
        }
        val context = NakshaContext.currentContext()
        if (!context.su) throw NakshaException(FORBIDDEN, "The context requires supervisor rights ('su') to initialize a new storage")
        // ...
        if (_channel == null) _channel = "lib-psql-${id}"
        if (!this::listener.isInitialized) listener = PsqlStorageListener(this)
    }
    override fun newSession(options: SessionOptions, readOnly: Boolean): PsqlSession = PsqlSession(this, options, readOnly)
    override val defaultMap: PsqlMap = super.defaultMap as PsqlMap
    override operator fun get(mapId: String): PsqlMap = super.get(mapId) as PsqlMap
}