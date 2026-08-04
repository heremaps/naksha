package naksha.model.objects

import naksha.base.FeatureType
import naksha.base.Id
import naksha.base.BaseUtil
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.ISession
import naksha.model.IStorage
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The database descriptor.
 *
 * Databases are containers that store [catalogs][NakshaCatalog], [collections][NakshaCollection] and [objects][naksha.base.PAnyMap]. You can think of them like physical disks. By definition, the database descriptor is a normal feature that follows the [XYZ][XyzMembers] layout. It is stored by the storage driver in a virtual collection named `naksha~databases`. The real location is dependent on the storage implementation.
 *
 * ### Warning
 * In the current implementation, each storage can only have one database. This is subject to change before finally releasing version 3. This feature is currently only a wrapper around a storage, but it will be decoupled soon.
 * @since 3.0
 * @see NakshaStorage
 * @see NakshaCatalog
 */
@Suppress("unused")
@OptIn(ExperimentalJsExport::class)
@JsExport
class NakshaDatabase() : NakshaFeature() {
    override fun withId(value: Id?): NakshaDatabase = super.withId(value) as NakshaDatabase
    override fun withType(value: String?): NakshaDatabase = super.withType(value) as NakshaDatabase
    override fun withFeatureType(value: FeatureType?): NakshaDatabase = super.withFeatureType(value) as NakshaDatabase
    override fun featureTypeDefaultValue(): FeatureType = FeatureType.DATABASE
    override fun withBbox(value: SpBoundingBox?): NakshaDatabase = super.withBbox(value) as NakshaDatabase
    override fun withGeometry(value: SpGeometry?): NakshaDatabase = super.withGeometry(value) as NakshaDatabase
    override fun withReferencePoint(value: SpPoint?): NakshaDatabase = super.withReferencePoint(value) as NakshaDatabase
    override fun withProperties(value: NakshaProperties): NakshaDatabase = super.withProperties(value) as NakshaDatabase
    override fun withMomType(value: String?): NakshaDatabase = super.withMomType(value) as NakshaDatabase

    /**
     * Create a new database descriptor for the default database of the storage.
     *
     * This method is a workaround until the database is correctly implemented by `lib-psql` and supported by Naksha-Hub.
     * @param storage the storage from which to acquire the database.
     * @since 3.0
     */
    @JsName("newDatabaseFromStorageDefault")
    constructor(storage: IStorage): this() {
        withType(typeDefaultValue())
        this.id = storage.defaultDatabaseId
        withFeatureType(featureTypeDefaultValue())
    }

    /**
     * Create a new database descriptor for the database used in the given session.
     *
     * This method is a workaround until the database is correctly implemented by `lib-psql` and supported by Naksha-Hub.
     * @param session the session from which to acquire the database.
     * @since 3.0
     */
    @JsName("newDatabaseFromSessionOptions")
    constructor(session: ISession): this() {
        withType(typeDefaultValue())
        this.id = session.options.databaseId
        withFeatureType(featureTypeDefaultValue())
    }

    /**
     * Create a new database descriptor with the given identifier.
     * @param id the database identifier to set.
     * @since 3.0
     */
    @JsName("newDatabaseWithId")
    constructor(id: Id): this() {
        withType(typeDefaultValue())
        this.id = id
        withFeatureType(featureTypeDefaultValue())
    }

    /**
     * Create a new database descriptor with the given identifier.
     * @param id the database identifier to set.
     * @since 3.0
     */
    @JsName("newDatabase")
    constructor(id: String?): this() {
        withType(typeDefaultValue())
        this.id = Id(id ?: BaseUtil.randomAtoZ())
        withFeatureType(featureTypeDefaultValue())
    }
}