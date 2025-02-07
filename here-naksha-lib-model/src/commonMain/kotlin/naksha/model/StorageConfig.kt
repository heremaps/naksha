@file:OptIn(ExperimentalJsExport::class)

package naksha.model

import naksha.base.*
import naksha.model.objects.NakshaFeature
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The base class for all storage configurations.
 *
 * This base class contains the minimal configuration needed, actual implementations may need more configuration properties.
 *
 * ### Note
 * There is no default-map configuration available, because the intention is that the users, for example Naksha-Hub, does pin the default map into the thread-local [context][NakshaContext.mapId]. This has the advantage, that potential clients can request to use a specific map for all requests, and everything that happens automatically uses this context map. Apart from this, that means that all storages always use the same default map-id, being `unimap`, which avoids that clients have to guess the default map.
 * @since 3.0.0
 */
@Suppress("unused")
@JsExport
open class StorageConfig : NakshaFeature() {

    companion object StorageConfig_C {
        private val ID = NotNullProperty<StorageConfig, String>(String::class)
        private val CLASSNAME = NotNullProperty<StorageConfig, String>(String::class) { self, _ -> self.defaultClassName() }
        private val NUMBER = NotNullProperty<StorageConfig, Int64>(Int64::class) { self, _ -> Naksha.storageNumberByHash(self.id) }
        private val HARDCAP = NotNullProperty<StorageConfig, Int>(Int::class) { _, _ -> 0 }
        private val CREATE = NotNullProperty<StorageConfig, Boolean>(Boolean::class) { _, _ -> false }
        private val UPGRADE = NotNullProperty<StorageConfig, Boolean>(Boolean::class) { _, _ -> false }
    }

    /**
     * The default classname to use, if any.
     * @return default classname, when none otherwise specified.
     */
    protected open fun defaultClassName(): String? = null

    /**
     * The unique storage-number.
     *
     * If not set explicitly, the default value will be generated as lower 52-bit (big-endian) of the MD5-hash above the [id].
     * @since 3.0.0
     */
    var number by NUMBER

    /**
     * Set the unique storage-number.
     * @param number the unique storage-number.
     * @return this.
     * @since 3.0.0
     */
    fun withNumber(number: Int64): StorageConfig {
        this.number = number
        return this
    }

   /**
     * The Kotlin name of the class to instantiate.
     * @since 3.0.0
     */
    var className by CLASSNAME

    /**
     * Set the unique class-name.
     * @param className the unique class-name.
     * @return this.
     * @since 3.0.0
     */
    fun withClassName(className: String): StorageConfig {
        this.className = className
        return this
    }

    /**
     * If _true_, then the storage will create missing structures when being initialized; if _false_, the initialization does not modify the storage, but rather throw an [NakshaError.INITIALIZATION_FAILED].
     * @since 3.0.0
     */
    var create by CREATE

    /**
     * Set [create] state.
     * @param create create state.
     * @return this.
     * @since 3.0.0
     */
    fun withCreate(create: Boolean): StorageConfig {
        this.create = create
        return this
    }

    /**
     * if _true_, then the storage will upgrade the admin-structures (e.g. stored procedures), when necessary; if _false_, the initialization does not modify the storage, but rather throw an [NakshaError.INITIALIZATION_FAILED].
     * @since 3.0.0
     */
    var upgrade by UPGRADE

    /**
     * Set [upgrade] state.
     * @param upgrade upgrade state.
     * @return this.
     * @since 3.0.0
     */
    fun withUpgrade(upgrade: Boolean): StorageConfig {
        this.upgrade = upgrade
        return this
    }

    /**
     * The hard-cap (limit) of the storage. No result-set every should become bigger than this amount of features.
     *
     * Setting the value is optionally support, storages may throw an [NakshaError.ILLEGAL_ARGUMENT] exception, when trying to modify the hard-cap, or they may only allow certain values and throw an [NakshaError.ILLEGAL_ARGUMENT] exception, if the value too big. Zero and negative values are changed into the maximum of whatever the storage supports, [Int.MAX_VALUE] means no hard-cap (if supported by the storage).
     *
     * Note that technically, due to binary encoding, there is normally a hard-cap at `16777216`.
     * @since 3.0.0
     */
    var hardCap by HARDCAP

    /**
     * Set [hardCap].
     * @param hardCap the new hard-cap.
     * @return this.
     * @since 3.0.0
     */
    fun withHardCap(hardCap: Int): StorageConfig {
        this.hardCap = hardCap
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (other is StorageConfig) return super.contentDeepEquals(other)
        return false
    }

    override fun hashCode(): Int {
        return id.hashCode() xor
            number.hashCode() xor
            className.hashCode() xor
            create.hashCode() xor
            upgrade.hashCode() xor
            hardCap.hashCode()
    }
}