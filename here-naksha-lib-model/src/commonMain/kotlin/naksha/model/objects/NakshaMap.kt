@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.Flags
import naksha.model.Naksha
import naksha.model.NakshaError
import naksha.model.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A map within a storage; maps are used to group collections.
 * @since 3.0.0
 */
@JsExport
open class NakshaMap() : NakshaFeature() {

    /**
     * Create a new map feature with the given ID.
     * @param storageId the storage-id in which the map is stored.
     * @param id the identifier to set.
     * @since 3.0.0
     */
    @Suppress("LeakingThis")
    @JsName("of")
    constructor(storageId: String, id: String): this() {
        this.storageId = storageId
        this.id = id
        this.type = defaultType()
        this.featureType = defaultFeatureType()
    }

    companion object NakshaMap_C {
        /**
         * The default map.
         * @since 3.0.0
         */
        const val DEFAULT = "unimap"

        /**
         * The feature-type of this feature itself.
         * @since 3.0.0
         */
        const val FEATURE_TYPE = "naksha.Map"

        private val STORAGE_ID = NotNullProperty<NakshaMap, String>(String::class) { _, _ -> throw NakshaException(NakshaError.ILLEGAL_STATE, "Missing storage-id for NakshaMap") }
        private val DEFAULT_FLAGS = NullableProperty<NakshaMap, Flags>(Flags::class)
    }

    override fun defaultFeatureType(): String = NakshaCollection.FEATURE_TYPE
    override fun withId(value: String): NakshaMap = super.withId(value) as NakshaMap
    override fun withFeatureNumber(featureNumber: Int64?): NakshaMap = super.withFeatureNumber(featureNumber) as NakshaMap
    override fun withType(value: String): NakshaMap = super.withType(value) as NakshaMap
    override fun withFeatureType(value: String): NakshaMap = super.withFeatureType(value) as NakshaMap
    override fun withBbox(value: SpBoundingBox?): NakshaMap = super.withBbox(value) as NakshaMap
    override fun withGeometry(value: SpGeometry?): NakshaMap = super.withGeometry(value) as NakshaMap
    override fun withReferencePoint(value: SpPoint?): NakshaMap = super.withReferencePoint(value) as NakshaMap
    override fun withProperties(value: NakshaProperties): NakshaMap = super.withProperties(value) as NakshaMap
    override fun withAttachment(value: ByteArray?): NakshaMap = super.withAttachment(value) as NakshaMap
    override fun withMomType(value: String): NakshaMap = super.withMomType(value) as NakshaMap

    /**
     * The storage-id of the storage in which the map is located.
     * - Throws [NakshaError.ILLEGAL_STATE] if read before set.
     * @since 3.0
     */
    var storageId by STORAGE_ID

    /**
     * @see storageId
     */
    open fun withStorageId(value: String): NakshaMap {
        storageId = value
        return this
    }

    /**
     * The encoding flags to be used for new rows of all collections of this map, that do not have an own [defaultFlags][NakshaCollection.defaultFlags].
     *
     * - If _null_, the storage will use whatever is best for the storage.
     * @since 3.0.0
     */
    var defaultFlags by DEFAULT_FLAGS

    private var _cachedId: String? = null
    private var _cachedNumber: Int? = null

    /**
     * The map-number, when internally _null_, then the number is generated as hash above the `id`.
     *
     * **{Create-Only}** - after map creation, modification of this parameter takes no effect.
     * @since 3.0
     */
    var number: Int
        get() {
            val id = this.id
            if (id == Naksha.ADMIN_MAP) return Naksha.ADMIN_MAP_NUMBER
            val n = getRaw("number")
            if (n is Int) return n
            val cachedId = _cachedId
            val cachedNumber = _cachedNumber
            if (id === cachedId && cachedNumber != null) return cachedNumber
            val md5 = Naksha.hashId(id)
            val number = Naksha.collectionNumber(md5)
            _cachedId = id
            _cachedNumber = number
            return number
        }
        set(value) {
            withNumber(value)
        }

    /**
     * @see [number]
     */
    open fun withNumber(value: Int?): NakshaMap {
        if (value == null) removeRaw("number") else setRaw("number", value)
        return this
    }
}