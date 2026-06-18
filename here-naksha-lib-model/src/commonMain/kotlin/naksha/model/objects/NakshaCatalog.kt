@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Int64
import naksha.base.NullableProperty
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.Naksha
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.TupleNumber
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

        private val DATABASE_ID = NullableProperty<NakshaCatalog, String>(String::class)
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
     * Helper to get/set the [TupleNumber] of the catalog-feature. All catalogs features follow the old XYZ-Hub style, therefore the location of the [TupleNumber] is clear.
     * @since 3.0
     */
    var tupleNumber: TupleNumber?
        get() = XyzMembers.XyzTn.getTupleNumber(this)
        set(value) {
            XyzMembers.XyzTn.set(this, value)
        }

    /**
     * The database-number of the catalog; the catalog-feature itself is stored in the same database as the catalog it describes.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE], when the collection does not have a valid [tupleNumber].
     */
    val databaseNumber: Int64
        get() = tupleNumber?.databaseNumber ?: throw NakshaException(ILLEGAL_STATE, "The collection has no tuple-number")

    /**
     * The database-id of the collection; **NOT** the database-id of the collection-feature itself, even while they are guaranteed to be the same.
     * @since 3.0
     */
    var databaseId: String? by DATABASE_ID

    /**
     * @see [databaseId]
     */
    fun withDatabaseId(value: String): NakshaCatalog {
        val tn = tupleNumber
        if (tn != null) {
            if (Naksha.databaseNumber(value) != tn.databaseNumber) {
                throw NakshaException(ILLEGAL_ARGUMENT, "The given database-id does not match the database-number of the collection.")
            }
        }
        databaseId = value
        return this
    }

    /**
     * The catalog-number of the catalog, this is actually the same as the feature-number.
     *
     * It is **NOT** the catalog-number of this catalog-feature, so where the catalog-feature itself is stored, which has always the catalog-number `0`, because all catalog features are always stored in the catalog `naksha~admin`.
     * @since 3.0
     * @see [Naksha.catalogNumber]
     */
    val catalogNumber: Int
        get() = Naksha.catalogNumber(id)
}