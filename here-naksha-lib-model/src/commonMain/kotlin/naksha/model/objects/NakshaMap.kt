@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.*
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A map within a storage; maps are used to group collections.
 * @since 3.0
 * @see NakshaObject
 * @see NakshaStorage
 * @see NakshaMap
 * @see NakshaCollection
 * @see NakshaDictionary
 * @see NakshaSubscriptionState
 * @see NakshaTx
 */
@JsExport
open class NakshaMap() : NakshaObject() {

    /**
     * Create a new map feature with the given identifier.
     * @param id the identifier to set.
     * @since 3.0
     */
    @JsName("NakshaMapOf")
    constructor(id: String): this() {
        this.id = id
    }

    companion object NakshaMap_C {
        /**
         * The [PlatformType] of [NakshaMap].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaMap::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("naksha.Map")

        private val STORAGE_ID = NullableProperty<NakshaMap, String>(String_TYPE)
        private val DEFAULT_FLAGS = NullableProperty<NakshaMap, Flags>(Int_TYPE)
    }

    override val properties: NakshaProperties
        get() = getProperties(NakshaProperties.TYPE)

    /**
     * The encoding flags to be used for new rows of all collections of this map, that do not have an own [defaultFlags][NakshaCollection.defaultFlags].
     *
     * - If _null_, the storage will use whatever is best for the storage.
     * @since 3.0
     */
    var defaultFlags by DEFAULT_FLAGS

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
}