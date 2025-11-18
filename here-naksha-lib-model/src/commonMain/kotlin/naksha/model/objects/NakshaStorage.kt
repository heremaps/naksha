@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.NakshaContext
import naksha.model.Naksha
import naksha.model.NakshaError
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaException
import kotlin.js.*
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A Naksha storage configuration.
 *
 * This class contains the minimal configuration needed for all storages, actual implementations may need more configuration properties.
 *
 * ### Note
 * There is no default-map configuration available, it's expected that clients provide the `mapId` explicitly.
 * @since 3.0
 */
@Suppress("unused")
@JsExport
open class NakshaStorage() : NakshaFeature() {

    /**
     * Create a new storage with the given identifier and class-name.
     * @param id the identifier to set.
     * @param className the full qualified name of the class to instantiate.
     * @since 3.0
     */
    @JsName("of")
    constructor(id: String, className: String) : this() {
        this.id = id
        this.className = className
    }

    companion object StorageConfig_C {
        /**
         * The feature-type of this feature itself _(`naksha.Storage`)_.
         * @since 3.0
         */
        const val FEATURE_TYPE = "naksha.Storage"

        const val CLASSNAME_FIELD = "className"

        private val ID = NotNullProperty<NakshaStorage, String>(String::class)
        private val CLASSNAME = NotNullProperty<NakshaStorage, String>(String::class, CLASSNAME_FIELD) { self, _ -> self.defaultClassName() }
        private val HARDCAP = NotNullProperty<NakshaStorage, Int>(Int::class) { _, _ -> 0 }
        private val CREATE = NotNullProperty<NakshaStorage, Boolean>(Boolean::class) { _, _ -> false }
        private val UPGRADE = NotNullProperty<NakshaStorage, Boolean>(Boolean::class) { _, _ -> false }

        /**
         * Helper class to parse a JSON configuration into a [NakshaStorage].
         * - Throws [NakshaError.ILLEGAL_ARGUMENT] if the given JSON is invalid.
         * @param json the JSON string.
         * @param fromJsonOptions optional parser options, defaults to [FromJsonOptions.DEFAULT].
         * @return the parsed JSON configuration.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun fromJSON(json: String, fromJsonOptions: FromJsonOptions? = null): NakshaStorage {
            try {
                return (Platform.fromJSON(json, fromJsonOptions ?: FromJsonOptions.DEFAULT) as PlatformMap).proxy(NakshaStorage::class)
            } catch (e: Exception) {
                if (e is NakshaException) throw e
                throw NakshaException(ILLEGAL_ARGUMENT, "Failed to parse JSON: $json", e)
            }
        }
    }

    override fun featureTypeDefaultValue(): String = FEATURE_TYPE
    override fun withId(value: String): NakshaStorage = super.withId(value) as NakshaStorage
    override fun withFeatureNumber(value: Int64): NakshaStorage = super.withFeatureNumber(value) as NakshaStorage
    override fun withType(value: String): NakshaStorage = super.withType(value) as NakshaStorage
    override fun withFeatureType(value: String): NakshaStorage = super.withFeatureType(value) as NakshaStorage
    override fun withBbox(value: SpBoundingBox?): NakshaStorage = super.withBbox(value) as NakshaStorage
    override fun withGeometry(value: SpGeometry?): NakshaStorage = super.withGeometry(value) as NakshaStorage
    override fun withReferencePoint(value: SpPoint?): NakshaStorage = super.withReferencePoint(value) as NakshaStorage
    override fun withProperties(value: NakshaProperties): NakshaStorage = super.withProperties(value) as NakshaStorage
    override fun withMomType(value: String?): NakshaStorage = super.withMomType(value) as NakshaStorage

    /**
     * The default classname to use, if any.
     * @return default classname, when none otherwise specified.
     */
    protected open fun defaultClassName(): String? = null

   /**
     * The full qualified name of the class to instantiate.
     * @since 3.0
     */
    var className by CLASSNAME

    /**
     * Set the unique class-name.
     * @param className the unique class-name.
     * @return this.
     * @since 3.0
     */
    fun withClassName(className: String): NakshaStorage {
        this.className = className
        return this
    }

    /**
     * If _true_, then the storage will create missing structures when being initialized; if _false_, the initialization does not modify the storage, but rather throw an [NakshaError.INITIALIZATION_FAILED].
     * @since 3.0
     */
    var create by CREATE

    /**
     * Set [create] state.
     * @param create create state.
     * @return this.
     * @since 3.0
     */
    fun withCreate(create: Boolean): NakshaStorage {
        this.create = create
        return this
    }

    /**
     * if _true_, then the storage will upgrade the admin-structures (e.g. stored procedures), when necessary; if _false_, the initialization does not modify the storage, but rather throw an [NakshaError.INITIALIZATION_FAILED].
     * @since 3.0
     */
    var upgrade by UPGRADE

    /**
     * Set [upgrade] state.
     * @param upgrade upgrade state.
     * @return this.
     * @since 3.0
     */
    fun withUpgrade(upgrade: Boolean): NakshaStorage {
        this.upgrade = upgrade
        return this
    }

    /**
     * The hard-cap (limit) of the storage. No result-set every should become bigger than this amount of features.
     *
     * Setting the value is optionally support, storages may throw an [NakshaError.ILLEGAL_ARGUMENT] exception, when trying to modify the hard-cap, or they may only allow certain values and throw an [NakshaError.ILLEGAL_ARGUMENT] exception, if the value too big. Zero and negative values are changed into the maximum of whatever the storage supports, [Int.MAX_VALUE] means no hard-cap (if supported by the storage).
     *
     * Note that technically, due to binary encoding, there is normally a hard-cap at `16777216`.
     * @since 3.0
     */
    var hardCap by HARDCAP

    /**
     * Set [hardCap].
     * @param hardCap the new hard-cap.
     * @return this.
     * @since 3.0
     */
    fun withHardCap(hardCap: Int): NakshaStorage {
        this.hardCap = hardCap
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (other is NakshaStorage) return super.contentDeepEquals(other)
        return false
    }

    override fun hashCode(): Int {
        return id.hashCode() xor
            className.hashCode() xor
            create.hashCode() xor
            upgrade.hashCode() xor
            hardCap.hashCode()
    }

    override fun featureNumberOfId(id: String): Int64 = Naksha.storageNumber(id)

    /**
     * The number of the storage, which is basically [featureNumber].
     * @since 3.0
     */
    val number: Int64
        get() = featureNumber

    /**
     * Compares this configuration with another [NakshaStorage] instance.
     *
     * This method is designed to be overridden by subclasses that want to define
     * equality based on a subset of configuration fields rather than performing
     * a full object comparison.
     *
     * When not overridden, this method falls back to the standard [equals] implementation.
     *
     * @param other another [NakshaStorage] instance to compare against
     * @return `true` if the two configurations are considered equal under the
     *         comparison rules; otherwise `false`
     * @since 3.0
     */
    open fun configEquals(other: NakshaStorage) = this == other
}