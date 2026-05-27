@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.*
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A map within a storage; maps are used to group collections.
 * @since 3.0
 */
@JsExport
open class NakshaMap() : NakshaFeature() {

    /**
     * Create a new map feature with the given identifier.
     * @param id the identifier to set.
     * @since 3.0
     */
    @Suppress("LeakingThis")
    @JsName("of")
    constructor(id: String): this() {
        this.id = id
        this.type = typeDefaultValue()
        this.featureType = featureTypeDefaultValue()
    }

    companion object NakshaMap_C {
        /**
         * The feature-type of this feature itself _(`naksha.Map`)_.
         * @since 3.0
         */
        const val FEATURE_TYPE = "naksha.Map"

        private val STORAGE_ID = NullableProperty<NakshaMap, String>(String::class)
        private val DATA_ENCODING = NullableEnum<NakshaMap, DataEncoding>(DataEncoding::class)
    }

    override fun featureTypeDefaultValue(): String = FEATURE_TYPE
    override fun withId(value: String): NakshaMap = super.withId(value) as NakshaMap
    override fun withFeatureNumber(value: Int64): NakshaMap = super.withFeatureNumber(value) as NakshaMap
    override fun withType(value: String): NakshaMap = super.withType(value) as NakshaMap
    override fun withFeatureType(value: String): NakshaMap = super.withFeatureType(value) as NakshaMap
    override fun withBbox(value: SpBoundingBox?): NakshaMap = super.withBbox(value) as NakshaMap
    override fun withGeometry(value: SpGeometry?): NakshaMap = super.withGeometry(value) as NakshaMap
    override fun withReferencePoint(value: SpPoint?): NakshaMap = super.withReferencePoint(value) as NakshaMap
    override fun withProperties(value: NakshaProperties): NakshaMap = super.withProperties(value) as NakshaMap
    override fun withMomType(value: String?): NakshaMap = super.withMomType(value) as NakshaMap

    /**
     * The feature encoding to use for new rows of all collections of this map that do not have an own [dataEncoding][NakshaCollection.dataEncoding].
     *
     * - If _null_, the storage will use [Naksha.DEFAULT_DATA_ENCODING].
     * @since 3.0
     */
    var dataEncoding by DATA_ENCODING

    override fun featureNumberOfId(id: String): Int64 = Naksha.mapNumber(id).toInt64()

    /**
     * The number of the map, which is basically [featureNumber].
     * @since 3.0
     */
    val number: Int
        get() = featureNumber.toInt()

    /**
     * Always return `2`, because all collections are always stored in `naksha~maps` collection.
     * @since 3.0
     * @see [Naksha.MAPS_COL]
     * @see [Naksha.MAPS_COL_NUMBER]
     */
    override val collectionNumber: Int
        get() = Naksha.MAPS_COL_NUMBER

    /**
     * Always return `0`, because all maps are always stored in `naksha~admin` map.
     * @since 3.0
     * @see [Naksha.ADMIN_MAP]
     * @see [Naksha.ADMIN_MAP_NUMBER]
     */
    override val mapNumber: Int
        get() = Naksha.ADMIN_MAP_NUMBER

    /**
     * The storage-id of the storage in which the map is located; `null` if not yet known.
     * @since 3.0
     */
    var storageId by STORAGE_ID

    /**
     * @see [storageId]
     */
    fun withStorageId(value: String?): NakshaMap {
        storageId = value
        return this
    }

}