package naksha.psql

import naksha.base.fn.Fx2
import naksha.base.unsupportedOp
import naksha.model.AbstractStorage
import naksha.model.IStreamSession
import naksha.model.IWriteSession
import naksha.model.SessionOptions
import kotlin.reflect.KClass

actual open class PgStorage() : AbstractStorage<PgConfig>() {
    @JsName("newPgStorage")
    actual constructor(config: PgConfig, create: Boolean?, upgrade: Boolean?) : this() {
        initializeStorage(config, create, upgrade)
    }

    private var _database: PgDatabase? = null
    private var _instance: PgInstance? = null

    actual override val configKlass: KClass<PgConfig> = PgConfig::class
    actual override val database: PgDatabase
        get() = _database ?: throwUninitialized()

    val instance: PgInstance
        get() = _instance ?: throwUninitialized()

    actual override fun newWriteSession(options: SessionOptions?): IWriteSession {
        return newSession(options ?: SessionOptions(), readOnly = false)
    }

    actual override fun newReadSession(options: SessionOptions?): IWriteSession {
        return newSession(options ?: SessionOptions(), readOnly = true)
    }

    actual override fun newStreamSession(options: SessionOptions?): IStreamSession {
        throw unsupportedOp("PgStorage.newStreamSession")
    }

    actual open fun newSession(options: SessionOptions, readOnly: Boolean): PgSession {
        useInitialized()
        val storage = this
        return object : PgSession(storage, options, readOnly) {
            override fun initConnection(conn: PgConnection, query: String) {
                // Borrow the invoking backend without applying JVM-style session settings.
            }
        }
    }

    actual open fun newConnection(
        options: SessionOptions,
        readOnly: Boolean,
        init: Fx2<PgConnection, String>?
    ): PgConnection {
        // PLV8 wrappers share the caller's backend; initialization must not change its session settings.
        return instance.openConnection(options, readOnly, init ?: SKIP_INIT)
    }

    actual override fun initStorage(config: PgConfig, create: Boolean?, upgrade: Boolean?) {
        if (_instance == null) {
            _instance = PgInstance.of(config.master)
            val db = PgDatabaseImpl(this, this.id)
            db.init(
                doOverride = config.override ?: false,
                doCreate = create ?: config.create,
                doUpgrade = upgrade ?: config.upgrade,
                attachExisting = true
            )
            _database = db
        }
    }

    actual override fun afterInit() {
    }

    actual override fun shutdownStorage(dropCache: Boolean) {
    }

    actual companion object PgStorageCompanion {
        @JsStatic
        actual val SKIP_INIT: Fx2<PgConnection, String> = Fx2 { _, _ -> }
    }
}
