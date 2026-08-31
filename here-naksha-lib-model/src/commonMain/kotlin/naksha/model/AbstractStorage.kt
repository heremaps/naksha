package naksha.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import naksha.base.Action
import naksha.base.Action.Action_C.VERSION
import naksha.base.AtomicRef
import naksha.base.Platform
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.Naksha.NakshaCompanion.catalogNumber
import naksha.model.Naksha.NakshaCompanion.collectionNumber
import naksha.model.Naksha.NakshaCompanion.databaseNumber
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.objects.NakshaStorage
import kotlin.reflect.KClass
import kotlin.time.Clock

/**
 * The base class for all storage implementations.
 *
 * It is mandatory to extend this class when creating a storage, otherwise the caching sub-system won't work. Technically, the caching will only create an instance of a storage, when there is not yet one with the same configuration.
 * @since 3.0.0
 */
abstract class AbstractStorage<CONFIG : NakshaStorage> : IStorage {

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
    private var _number: Long? = null

    override val config: CONFIG
        get() = configRef.get() ?: throwUninitialized()

    override val id: String
        get() = _id ?: throwUninitialized()

    override val number: Long
        get() = _number ?: throwUninitialized()

    override var hardCap: Int = 16777216
        set(value) {
            if (value > 16777216) throw NakshaException(
                ILLEGAL_ARGUMENT,
                "The maximum hard-cap supported is 16777216, but $value was requested"
            )
            field = if (value <= 0) 16777216 else value
        }

    /**
     * Initializes the storage, invoked by [Naksha].
     *
     * If necessary, this method will create the storage structures to store transactions, install needed scripts, extensions, and do all other initialization works. If the storage is already initialized, the given storage-identifier, and storage-number, must match the existing ones, otherwise an [naksha.base.NakshaError.STORAGE_ID_MISMATCH] exception is raised. Setting up a new storage requires that the current [context][NakshaContext] has the [superuser][NakshaContext.su] rights, if this is not the case, an [naksha.base.NakshaError.FORBIDDEN] exception is raised.
     *
     * - Throws [naksha.base.NakshaError.FORBIDDEN], if not called as super-user, but super-user rights are necessary.
     * - Throws [naksha.base.NakshaError.INITIALIZATION_FAILED], if the initialization failed.
     * - Throws [naksha.base.NakshaError.STORAGE_ID_MISMATCH], if the existing _storage-id_ and/or _storage-number_ of the data does not match the given ones in the configuration.
     * - Throws [naksha.base.NakshaError.ILLEGAL_ARGUMENT], if any configuration entry is invalid, for example [NakshaStorage.hardCap] too large.
     * @param config the storage configuration as required.
     * @param create if not _null_, overrides [NakshaStorage.create].
     * @param upgrade if not _null_, overrides [NakshaStorage.upgrade].
     * @since 3.0.0
     */
    protected abstract fun initStorage(config: CONFIG, create: Boolean?, upgrade: Boolean?)

    /**
     * Called by caching sub-system, which is the only one actually invoking initStorage!
     * @param storage the storage configuration as required.
     * @param create if not _null_, overrides [NakshaStorage.create].
     * @param upgrade if not _null_, overrides [NakshaStorage.upgrade].
     */
    internal fun invokeInitStorage(storage: NakshaStorage, create: Boolean?, upgrade: Boolean?) {
        lock.acquire().use {
            if (configRef.get() == null || create==true || upgrade==true) {
                val _config = storage.proxy(configKlass)
                this._id = storage.id
                this._number = featureNumber(storage.id)
                this.hardCap = storage.hardCap
                initStorage(_config, create, upgrade)
                this.configRef.set(_config)
                afterInit()
            }
        }
    }

    /**
     * Creates a new [TupleNumber] for a feature in this storage, in the given catalog and collection.
     *
     * You need a version for features belonging together to the same transaction. You may use [newVirtualVersion] for this purpose, but beware that this is only a helper for mocks or storages that do not really support tuple-number, and where the tuple-number is only used internally within the storage.
     * @param catalogId the `id` of the catalog where the feature is contained.
     * @param collectionId the `id` of the collection where the feature is contained.
     * @param featureId the `id` of the feature being stored.
     * @param version the version, see [Version.virtualVersion].
     * @param action the action to encode in the [TupleNumber].
     * @return the new [TupleNumber] for the given feature.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize.
     */
    protected open fun newVirtualTupleNumber(catalogId: String, collectionId: String, featureId: String, version: Long, action: Action): TupleNumber {
        val v = (version and -4L) or action.longValue
        return TupleNumber(databaseNumber(id), catalogNumber(catalogId), collectionNumber(collectionId), featureNumber(featureId), v)
    }

    /**
     * The atomic that tracks the current version JVM local.
     *
     * **This only works on a single JVM and resets when the JVM is restarted. Synchronization and range split must be done by the extending class, if wished.**
     * @since 3.0
     * @see newVirtualVersion
     */
    protected open val nextVirtualVersion = Platform.newAtomicInt64(Version.now(0L, VERSION).number)

    /**
     * Creates a new JVM local unique transaction number, aka virtual version.
     *
     * **Warning: This is a helper for virtual storages or mock-ups.**
     * @return a new JVM local unique transaction version.
     * @since 3.0
     * @see nextVirtualVersion
     */
    protected open fun newVirtualVersion(): Version {
        // More reliable tailrec.
        while (true) {
            val version = Version(nextVirtualVersion.getAndAdd(4L))
            // getAndAdd uses a memory-fence, therefore `now` is guaranteed to be read after the version!
            val now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            if (version.isBehind(now.year, now.month.number, now.day)) {
                val newVersion = Version.auto(now.year, now.month.number, now.day, 0L, VERSION)
                if (nextVirtualVersion.compareAndSet(version.number + 4, newVersion.number + 4)) return newVersion
                // Fail, concurrent increment, repeat.
                continue
            }
            return version
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
     * Helper method to always throw the same exception, when the storage is not initialized, but initialization is required.
     */
    fun throwUninitialized(): Nothing {
        throw NakshaException(UNINITIALIZED, "initStorage not called")
    }

    /**
     * Ensures that the storage is initialized, otherwise throws an [naksha.base.NakshaError.UNINITIALIZED], to be used like `storage.useInitialized().id`.
     * @return this.
     * @since 3.0
     */
    fun useInitialized(): IStorage {
        if (!initialized) throwUninitialized()
        return this
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
        // Naksha.cache.removedStorage(this)
    }
}
