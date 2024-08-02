//package naksha.psql.write
//
//import naksha.base.Fnv1a32
//import naksha.base.Platform
//import naksha.model.*
//import naksha.model.objects.NakshaCollection
//import naksha.model.objects.NakshaFeature
//import naksha.psql.*
//import naksha.psql.TRANSACTIONS_COL
//import kotlin.jvm.JvmField
//
//open class RowUpdater(@JvmField val session: PgSession) {
//
//    /**
//     * Returns the [flags][Flags] to use, when encoding new rows.
//     * @return the [flags][Flags] to use, when encoding new rows.
//     */
//    protected open fun flags(collection: NakshaCollection): Flags = collection.defaultFlags ?: session.storage.defaultFlags()
//
//    /**
//     * Returns the encoding to store for the [feature-type][Metadata.type].
//     * @param collection the collection in which to store the feature.
//     * @param feature the feature.
//     * @return the type to store in [Metadata].
//     */
//    protected open fun featureType(collection: PgCollection, feature: NakshaFeature): String? {
//        val type = feature.momType ?: feature.properties.featureType ?: feature.type
//        return if (type == collection.nakshaCollection.defaultType) null else type
//    }
//
//    /**
//     * Create the XYZ namespace for an _INSERT_ operation.
//     * @param row the row as created from a feature.
//     * @return a new row with updated metadata.
//     */
//    fun xyzInsert(collection: PgCollection, feature: NakshaFeature): Metadata {
//        val txn = session.txn()
//        val now = session.txnTs()
//        return Metadata(
//            updatedAt = now,
//            flags = flags(collection.nakshaCollection),
//            author = session.options.author,
//            appId = session.options.appId,
//            type = featureType(collection, feature)
//        )
//    }
//
//    /**
//     *  Prepares XyzNamespace columns for head table.
//     */
//    internal fun xyzUpdateHead(collectionId: String, NEW: PgRow, OLD: PgRow) {
//        xyzInsert(collectionId, NEW)
//        val updatedAt = Platform.currentMillis()
//        if (session.options.author == null) {
//            NEW.author = OLD.author
//            NEW.author_ts = OLD.author_ts
//        } else {
//            NEW.author = session.options.author
//            NEW.author_ts = NEW.updated_at
//        }
//        NEW.version = (OLD.version ?: 0) + 1
//        NEW.ptxn = OLD.txn
//        NEW.puid = OLD.uid
//        if (collectionId == TRANSACTIONS_COL) {
//            NEW.updated_at = updatedAt
//        }
//    }
//
//    /**
//     * Prepares row before putting into $del table.
//     */
//    internal fun xyzDel(OLD: PgRow) {
//        val txn = session.txn()
//        val txnTs = session.txnTs()
//        OLD.txn = txn.txn
//        OLD.txn_next = txn.txn
//        OLD.flags = Flags(OLD.flags!!).action(Action.DELETED)
//        OLD.author = session.options.author ?: session.options.appId
//        if (session.options.author != null) {
//            OLD.author_ts = txnTs
//        }
//        if (OLD.created_at != null) {
//            OLD.created_at = OLD.updated_at
//        }
//        OLD.updated_at = txnTs
//        OLD.app_id = session.options.appId
//        OLD.uid = session.nextUid()
//        val currentVersion: Int = OLD.version ?: 1
//        OLD.version = currentVersion + 1
//    }
//
//    /**
//     * Create a GRID for the given row.
//     *
//     * The GRID is the HERE tile ID at level 15, calculated either used for distributed processing of features. This method uses GeoHash at level 14, which uses
//     * 34 bits for latitude and 36 bits for longitude (70-bit total). The precision is therefore higher than 1mm.
//     *
//     * If the feature does not have a geometry, this method creates a pseudo GRID (GeoHash Reference ID) from the
//     * given feature-id, this is based upon [Geohash](https://en.wikipedia.org/wiki/Geohash#Textual_representation).
//     *
//     * See [https://www.movable-type.co.uk/scripts/geohash.html](https://www.movable-type.co.uk/scripts/geohash.html)
//     * @param id The feature-id.
//     * @param flags The geometry type.
//     * @param geo The feature geometry; if any.
//     * @return The GRID (14 character long string).
//     */
//    internal fun grid(row: Row): Int {
//        // FIXME TODO use point to here tile function after merge
//        return 0
////        if (geo == null) return Static.gridFromId(id)
////        if (!this::gridPlan.isInitialized) {
////            gridPlan = sql.prepare("SELECT ST_GeoHash(ST_Centroid(naksha_geometry($1::int2,$2::bytea)),14) as hash", arrayOf(SQL_INT16, SQL_BYTE_ARRAY))
////        }
////        return asMap(asArray(gridPlan.execute(arrayOf(flags, geo)))[0])["hash"]!!
//    }
//
//    /**
//     * Calculates hash of a row.
//     *
//     * @param row the row.
//     * @return the hash.
//     */
//    internal fun rowHash(row: Row): Int {
//        var totalHash = Fnv1a32.hashByteArray(row.feature)
//        totalHash = Fnv1a32.hashByteArray(row.tags, totalHash)
//        totalHash = Fnv1a32.hashByteArray(row.geo, totalHash)
//        totalHash = Fnv1a32.hashByteArray(row.referencePoint, totalHash)
//        return totalHash
//    }
//}
