@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.model.objects

import naksha.base.*
import naksha.geo.BBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.Naksha
import naksha.base.NakshaError
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.*
import kotlin.jvm.JvmField

/**
 * A Naksha storage configuration.
 *
 * This class contains the minimal configuration needed for all storages, actual implementations may need more configuration properties.
 *
 * ### Note
 * There is no default-map configuration available, it's expected that clients provide the `mapId` explicitly.
 * @since 3.0
 * @see NakshaObject
 * @see NakshaStorage
 * @see NakshaMap
 * @see NakshaCollection
 * @see NakshaDictionary
 * @see NakshaSubscriptionState
 * @see NakshaTx
 */
@Suppress("unused")
@JsExport
open class NakshaStorage() : NakshaObject() {

    /**
     * Create a new storage with the given identifier and class-name.
     * @param id the identifier to set.
     * @param className the full qualified name of the class to instantiate.
     * @since 3.0
     */
    @JsName("NakshaStorageOf")
    constructor(id: String, className: String) : this() {
        this.id = id
        this.className = className
    }

    companion object NakshaStorage_C {
        /**
         * The [PlatformType] of [NakshaStorage].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaStorage::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("naksha.Storage")

        const val CLASSNAME_FIELD = "className"

        private val ID = NotNullProperty<NakshaStorage, String>(String_TYPE)
        private val CLASSNAME = NotNullProperty<NakshaStorage, String>(String_TYPE, CLASSNAME_FIELD) { self, _ -> self.defaultClassName() }
        private val HARDCAP = NotNullProperty<NakshaStorage, Int>(Int_TYPE) { _, _ -> 0 }
        private val CREATE = NotNullProperty<NakshaStorage, Boolean>(Boolean_TYPE) { _, _ -> false }
        private val UPGRADE = NotNullProperty<NakshaStorage, Boolean>(Boolean_TYPE) { _, _ -> false }
    }

    override fun withId(id: String): NakshaStorage = super.withId(id) as NakshaStorage
    override fun withBBox(bbox: BBox): NakshaStorage = super.withBBox(bbox) as NakshaStorage
    override fun withAutoBBox(): NakshaStorage = super.withAutoBBox() as NakshaStorage
    override fun withGeometry(geometry: SpGeometry?): NakshaStorage = super.withGeometry(geometry) as NakshaStorage
    override val properties: NakshaProperties
        get() = get_properties(NakshaProperties.TYPE)
    override fun withProperties(properties: AnyObject): NakshaStorage = super.withProperties(properties) as NakshaStorage
    override fun withFeatureNumber(value: Int64): NakshaStorage = super.withFeatureNumber(value) as NakshaStorage
    override fun withReferencePoint(value: SpPoint?): NakshaStorage = super.withReferencePoint(value) as NakshaStorage

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
}