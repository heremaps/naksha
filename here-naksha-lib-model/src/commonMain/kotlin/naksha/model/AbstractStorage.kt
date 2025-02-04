package naksha.model

import naksha.base.AtomicRef
import naksha.base.Int64
import naksha.base.Platform
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import kotlin.reflect.KClass

/**
 * The base class for all storage implementations.
 *
 * It is mandatory to extend this class when creating a storage, otherwise the caching sub-system won't work. Technically, the caching will only create an instance of a storage, when there is not yet one with the same configuration.
 * @since 3.0.0
 */
abstract class AbstractStorage<CONFIG : StorageConfig> : IStorage {

    /**
     * A lock for the storage to synchronize access to some properties and to prevent, that multiple threads in parallel initialize the storage, can be used by applications too, but should be avoided.
     *
     * @since 3.0.0
     */
    override val lock = Platform.newLock()

    /**
     * The _KClass_ of the configuration needed.
     * @since 3.0.0
     */
    abstract val configKlass: KClass<CONFIG>

    /**
     * The atomic reference to the configuration for internal use within [initStorage].
     * @since 3.0.0
     */
    protected var configRef: AtomicRef<CONFIG> = AtomicRef(null)
    private var _id: String? = null
    private var _number: Int64? = null

    override val config: CONFIG
        get() = configRef.get() ?: throw NakshaException(UNINITIALIZED, "initStorage not called")

    override val id: String
        get() = _id ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    override val number: Int64
        get() = _number ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    override var hardCap: Int = 16777216
        set(value) {
            if (value > 16777216) throw NakshaException(ILLEGAL_ARGUMENT, "The maximum hard-cap supported is 16777216, but $value was requested")
            field = if (value <= 0) 16777216 else value
        }

    /**
     * Initializes the storage, invoked by [Naksha].
     *
     * If necessary, this method will create the storage structures to store transactions, install needed scripts, extensions, and do all other initialization works. If the storage is already initialized, the given storage-identifier, and storage-number, must match the existing ones, otherwise an [NakshaError.STORAGE_ID_MISMATCH] exception is raised. Setting up a new storage requires that the current [context][NakshaContext] has the [superuser][NakshaContext.su] rights, if this is not the case, an [NakshaError.FORBIDDEN] exception is raised.
     *
     * - Throws [NakshaError.FORBIDDEN], if not called as super-user, but super-user rights are necessary.
     * - Throws [NakshaError.INITIALIZATION_FAILED], if the initialization failed.
     * - Throws [NakshaError.STORAGE_ID_MISMATCH], if the existing _storage-id_ and/or _storage-number_ of the data does not match the given ones in the configuration.
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if any configuration entry is invalid, for example [StorageConfig.hardCap] too large.
     * @param config the configuration as required.
     * @param create if not _null_, overrides [StorageConfig.create].
     * @param upgrade if not _null_, overrides [StorageConfig.upgrade].
     * @since 3.0.0
     */
    protected abstract fun initStorage(config: CONFIG, create: Boolean?, upgrade: Boolean?)

    // Called by caching sub-system, which is the only one actually invoking initStorage!
    internal fun invokeInitStorage(config: StorageConfig, create: Boolean?, upgrade: Boolean?) {
        lock.acquire().use {
            if (configRef.get() == null || create==true || upgrade==true) {
                val _config = config.proxy(configKlass)
                this.hardCap = config.hardCap
                initStorage(_config, create, upgrade)
                this._id = config.id
                this._number = config.number
                this.configRef.set(_config)
                afterInit()
            }
        }
    }

    /**
     * Helper method invoked by [initStorage] after the initialization has been done successfully, so just after [id], and [number] were set, but before the [lock] is released.
     * @since 3.0.0
     */
    protected abstract fun afterInit()

    /**
     * Shutdown the storage instance, blocks until the storage is down (all sessions are closed).
     *
     * This method will remove the instance from the [Naksha] singleton.
     *
     * @param dropCache if the cache should be dropped (new mandatory argument with v3.0.0).
     * @since 3.0.0
     */
    protected abstract fun shutdownStorage(dropCache: Boolean)

    // Needed by caching sub-system only!
    internal fun invokeShutdownStorage(dropCache: Boolean) {
        shutdownStorage(dropCache)
    }

    /**
     * Mainly for internal purpose, tests if the storage is already initialized.
     *
     * @since 3.0.0
     * @return _true_ if the storage is initialized; _false_ otherwise.
     */
    val initialized: Boolean
        get() = configRef.get() != null

    /**
     * Helper method to be called by the storage, ones a [shutdownStorage] is done, to finally remove the cache.
     * @since 3.0.0
     */
    protected open fun dropCache() {
        Naksha.cache.removedStorage(this)
    }
}