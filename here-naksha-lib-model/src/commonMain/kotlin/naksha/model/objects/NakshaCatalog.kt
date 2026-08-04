@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Id
import naksha.base.FeatureType
import naksha.base.NakshaException
import naksha.base.NotNullIdProperty
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.ISession
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The catalog descriptor.
 *
 * Catalogs are containers that store [collections][NakshaCollection] and [objects][naksha.base.PAnyMap]. You can think of them like a directory on a physical disk _(aka database)_. By definition, the catalog descriptor is a normal feature that follows the [XYZ][XyzMembers] layout. It is stored in a virtual internal collection named `naksha~catalogs`. The actual location is dependent on the storage implementation.
 *
 * @since 3.0
 * @see NakshaDatabase
 * @see NakshaCollection
 */
@JsExport
open class NakshaCatalog() : NakshaFeature() {

    companion object NakshaCatalog_C {
        private val ID_NOT_NULL = NotNullIdProperty<NakshaCatalog>(randomId = false)
    }

    /**
     * Create a new catalog descriptor without `databaseId`, this has to be set later via [withDatabaseId]!
     * @param id the identifier of the catalog.
     * @since 3.0
     */
    @Deprecated("Please always provide a databaseId in some way")
    @JsName("newCatalog")
    constructor(id: Id): this() {
        withType(typeDefaultValue())
        this.id = id
        withFeatureType(featureTypeDefaultValue())
    }

    /**
     * Create a new catalog descriptor.
     * @param id the identifier of the catalog.
     * @param databaseId the `id` of the database in which the catalog is stored.
     * @since 3.0
     */
    @JsName("newCatalogForDatabaseId")
    constructor(id: Id, databaseId: Id): this() {
        withType(typeDefaultValue())
        this.id = id
        withFeatureType(featureTypeDefaultValue())
        this.databaseId = databaseId
    }

    /**
     * Create a new catalog descriptor.
     * @param id the identifier of the catalog.
     * @param session the session in which the catalog is used _(copies the database-id from session options)_.
     * @since 3.0
     */
    @JsName("newCatalogForSessionDatabase")
    constructor(id: Id, session: ISession): this() {
        withType(typeDefaultValue())
        this.id = id
        withFeatureType(featureTypeDefaultValue())
        databaseId = session.options.databaseId
    }

    /**
     * Create a new catalog descriptor.
     * @param id the identifier of the catalog.
     * @param database the database in which to create the catalog.
     * @since 3.0
     */
    @JsName("newCatalogForDatabase")
    constructor(id: Id, database: NakshaDatabase): this() {
        withType(typeDefaultValue())
        this.id = id
        withFeatureType(featureTypeDefaultValue())
        databaseId = database.id
    }

    override fun withId(value: Id?): NakshaCatalog = super.withId(value) as NakshaCatalog
    override fun withType(value: String?): NakshaCatalog = super.withType(value) as NakshaCatalog
    override fun withFeatureType(value: FeatureType?): NakshaCatalog = super.withFeatureType(value) as NakshaCatalog
    override fun featureTypeDefaultValue(): FeatureType? = FeatureType.CATALOG
    override fun withBbox(value: SpBoundingBox?): NakshaCatalog = super.withBbox(value) as NakshaCatalog
    override fun withGeometry(value: SpGeometry?): NakshaCatalog = super.withGeometry(value) as NakshaCatalog
    override fun withReferencePoint(value: SpPoint?): NakshaCatalog = super.withReferencePoint(value) as NakshaCatalog
    override fun withProperties(value: NakshaProperties): NakshaCatalog = super.withProperties(value) as NakshaCatalog
    override fun withMomType(value: String?): NakshaCatalog = super.withMomType(value) as NakshaCatalog

    /**
     * The `id` of the [database][NakshaDatabase] in which this [catalog][NakshaCatalog], and all its [collections][NakshaCollection] and [objects][naksha.base.PAnyMap] are stored.
     * @since 3.0
     * @throws NakshaException with error [naksha.base.NakshaError.ILLEGAL_STATE] if the [databaseId] is `null`, `undefined` or invalid.
     */
    var databaseId: Id by ID_NOT_NULL

    /**
     * Tests if this feature does have the property `databaseId` set.
     * @since 3.0
     */
    fun hasDatabaseId(): Boolean = hasIdValue("databaseId")

    /**
     * @see [databaseId]
     * @since 3.0
     */
    fun withDatabaseId(value: Id): NakshaCatalog {
        databaseId = value
        return this
    }
}