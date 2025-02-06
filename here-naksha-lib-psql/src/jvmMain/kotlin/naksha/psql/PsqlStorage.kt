package naksha.psql

import naksha.base.fn.Fx2
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import kotlin.reflect.KClass

/**
 * The Java implementation of the [PgStorage], classname `naksha.psql.JvmPgStorage`.
 *
 * @constructor Creates a new PSQL storage.
 * @property cluster the PostgresQL cluster used by this storage.
 * @param defaultSchemaName the default schema name.
 */
open class PsqlStorage : PgStorage(), IStorage {

    override val configKlass: KClass<PgConfig> = PgConfig::class

    override fun afterInit() {
        TODO("Not yet implemented")
    }

    override fun shutdownStorage(dropCache: Boolean) {
        TODO("Not yet implemented")
    }

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

    override fun initAdminMap(config: PgConfig, create: Boolean?, upgrade: Boolean?): PgAdminMap {
        return PsqlAdminMap(this, config, create, upgrade)
    }

    override fun newSession(options: SessionOptions, readOnly: Boolean): PsqlSession = PsqlSession(this, options, readOnly)
    override fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PgConnection {
        TODO("Not yet implemented")
    }

    override fun adminConnection(): PgConnection {
        TODO("Not yet implemented")
    }

    override fun getEncodingFlags(feature: Any?, context: Any?): Flags = adminMap.getEncodingFlags(feature, context)

    override fun getDictionary(id: String): JbDictionary? = adminMap.getDictionary(id)

    override fun getEncodingDictionary(feature: Any?, context: Any?): JbDictionary? = adminMap.getEncodingDictionary(feature, context)
}