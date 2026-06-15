@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Int64
import naksha.base.NullableProperty
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A map within a storage; maps are used to group collections.
 * @since 3.0
 */
@JsExport
open class NakshaCatalog() : NakshaFeature() {

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
         * The feature-type of this feature itself _(`naksha.Catalog`)_.
         * @since 3.0
         */
        const val FEATURE_TYPE = "naksha.Catalog"

        private val DATABASE_NUMBER = NullableProperty<NakshaCatalog, Int64>(Int64::class)
        private val DATABASE_ID = NullableProperty<NakshaCatalog, String>(String::class)
        private val CATALOG_NUMBER = NullableProperty<NakshaCatalog, Int>(Int::class)
    }

    override fun featureTypeDefaultValue(): String = FEATURE_TYPE
    override fun withId(value: String): NakshaCatalog = super.withId(value) as NakshaCatalog
    override fun withType(value: String): NakshaCatalog = super.withType(value) as NakshaCatalog
    override fun withFeatureType(value: String): NakshaCatalog = super.withFeatureType(value) as NakshaCatalog
    override fun withBbox(value: SpBoundingBox?): NakshaCatalog = super.withBbox(value) as NakshaCatalog
    override fun withGeometry(value: SpGeometry?): NakshaCatalog = super.withGeometry(value) as NakshaCatalog
    override fun withReferencePoint(value: SpPoint?): NakshaCatalog = super.withReferencePoint(value) as NakshaCatalog
    override fun withProperties(value: NakshaProperties): NakshaCatalog = super.withProperties(value) as NakshaCatalog
    override fun withMomType(value: String?): NakshaCatalog = super.withMomType(value) as NakshaCatalog

    /**
     * The database-number of the collection; **NOT** the database-number of the collection-feature itself, even while they are guaranteed to be the same.
     * @since 3.0
     */
    var databaseNumber: Int64? by DATABASE_NUMBER
    // TODO: Fix this, we need to calculate the database-number from the database-id, if an id is given!

    /**
     * The database-id of the collection; **NOT** the database-id of the collection-feature itself, even while they are guaranteed to be the same.
     * @since 3.0
     */
    var databaseId: String? by DATABASE_ID

    /**
     * @see [databaseId]
     */
    fun withDatabaseId(value: String?): NakshaCatalog {
        databaseId = value
        return this
    }

    /**
     * The catalog-number of the collection; **NOT** the catalog-number of the collection-feature itself, which would always be `0` _(`naksha~admin`)_.
     * @since 3.0
     */
    var catalogNumber: Int? by CATALOG_NUMBER
    // TODO: Fix this, we need to calculate the catalog-number from the catalog-id (aka `id`).
    //       Actually the feature-number of the catalog and catalog-number must be the same!
}