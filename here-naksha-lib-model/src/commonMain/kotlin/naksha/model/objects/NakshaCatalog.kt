@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.NullableProperty
import naksha.base.Id
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.base.NakshaException
import naksha.base.illegalState
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A custom catalog within a storage.
 *
 * Catalogs are used to group [collections][NakshaCollection]. Every catalog does have an [internal catalog collection][NakshaCatalogCollection] (`naksha~collections`) in which the collections of the catalog are managed.
 * @since 3.0
 * @see NakshaCatalogCollection
 */
@JsExport
open class NakshaCatalog() : NakshaFeature() {

    /**
     * Create a new catalog feature.
     * @param databaseId the identifier of the database in which the catalog is located.
     * @param catalogId the identifier of the catalog.
     * @since 3.0
     */
    @JsName("newCatalog")
    constructor(databaseId: Id, catalogId: Id): this() {
        this.databaseId = databaseId.text
        this.id = catalogId.text
        this.type = typeDefaultValue()
        this.featureType = featureTypeDefaultValue()
    }

    /**
     * Create a new catalog feature.
     * @param databaseId the identifier of the database in which the catalog is located.
     * @param catalogId the identifier of the catalog.
     * @since 3.0
     */
    @JsName("newCatalogText")
    constructor(databaseId: Id, catalogId: String): this() {
        this.databaseId = databaseId.text
        this.id = catalogId
        this.type = typeDefaultValue()
        this.featureType = featureTypeDefaultValue()
    }

    /**
     * Create a new map feature with the given identifier.
     * @param id the identifier to set.
     * @since 3.0
     */
    @Deprecated("Avoid creating catalogs without databaseId")
    @JsName("newCatalogByIdOnly")
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
     * The database-number of the database in which this catalog is located.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE], when the catalog does not have a valid [tupleNumber] and no valid [databaseId].
     */
    val databaseNumber: Long
        get() {
            val tn = tupleNumber
            if (tn != null) return tn.databaseNumber
            val id = this.databaseId
            if (id != null) Id.textToNumber(id)
            throw illegalState("The catalog has no database-id and no tuple-number")
        }

    /**
     * The database-id of the database in which this catalog is located.
     * @since 3.0
     */
    var databaseId: String? by DATABASE_ID

    /**
     * @see [databaseId]
     */
    fun withDatabaseId(value: String): NakshaCatalog {
        databaseId = value
        return this
    }

    /**
     * The catalog-number of the catalog, this is actually the same as the feature-number truncated to 32-bit.
     *
     * It is **NOT** the catalog-number of this catalog-feature, so where the catalog-feature itself is stored, which has always the catalog-number `0`, because all catalog features are always stored in the catalog `naksha~admin`.
     * @since 3.0
     * @see [Id.intValue]
     */
    val catalogNumber: Int
        get() = Id.numberToInt(Id.textToNumber(id))
}
