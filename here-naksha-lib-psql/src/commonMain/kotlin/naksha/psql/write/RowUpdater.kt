package naksha.psql.write

import naksha.base.Fnv1a32
import naksha.base.Int64
import naksha.base.Platform
import naksha.model.*
import naksha.psql.PgPlan
import naksha.psql.NakshaSession
import naksha.psql.PgStatic.SC_TRANSACTIONS

internal class RowUpdater(val session: NakshaSession) {
    private lateinit var gridPlan: PgPlan

    /**
     * Create the XYZ namespace for an _INSERT_ operation.
     * @param collectionId The collection into which a feature is inserted.
     * @param NEW The row in which to update the XYZ namespace columns.
     * @return The new XYZ namespace for this feature.
     */
    internal fun xyzInsert(collectionId: String, NEW: Row) {
        TODO("We need to adjust this, because Row is immutable now!")
        val txn = session.txn()
        val txnTs = session.txnTs()

        var geoGrid: Int? = NEW.meta?.geoGrid

        // FIXME: default flags should be taken from collectionConfig
        val flags = NEW.meta?.flags ?: Flags()

        if (geoGrid == null) {
            // Only calculate geo-grid, if not given by the client.
            val id: String = NEW.id
            geoGrid = grid(id, flags, NEW.geo)
        }

        val uid = if (collectionId == SC_TRANSACTIONS) {
            0
        } else {
            session.nextUid()
        }

//        val newMeta = Metadata(
//            id = NEW.id,
//            txn = txn.value,
//            txnNext = null,
//            ptxn = null,
//            puid = null,
//            geoGrid = geoGrid,
//            version = null, // saving space null means 1
//            uid = uid,
//            createdAt = null, // saving space - it is same as update_at at creation,
//            updatedAt = txnTs,
//            author = session.context.author,
//            authorTs = null, // saving space - only apps are allowed to create features
//            appId = session.context.appId,
//            flags = flags,
//            fnva1 = rowHash(NEW)
//        )
//        NEW.meta = newMeta
    }

    /**
     *  Prepares XyzNamespace columns for head table.
     */
    internal fun xyzUpdateHead(collectionId: String, NEW: Row, OLD: Row) {
        TODO("We need to adjust this, because Row is immutable now!")
        xyzInsert(collectionId, NEW)
        val oldMeta = OLD.meta
        require(oldMeta != null)
        val newMeta = NEW.meta
        val updatedAt = Platform.currentMillis()
        //val createdAt = oldMeta.createdAt ?:
        val author: String?
        val authorTs: Int64?
        if (session.context.author == null) {
            author = oldMeta?.author
            authorTs = oldMeta?.authorTs
        } else {
            author = session.context.author
            authorTs = newMeta!!.updatedAt
        }
        val version: Int = (oldMeta?.version ?: 0) + 1
        val ptxn = oldMeta?.txn
        val puid = oldMeta?.uid
//        if (collectionId == SC_TRANSACTIONS) {
//            newMeta.updatedAt =
//        }
    }

    /**
     * Prepares row before putting into $del table.
     */
    internal fun xyzDel(OLD: Metadata) {
        TODO("We need to adjust this, because Row is immutable now!")
//        val txn = session.txn()
//        val txnTs = session.txnTs()
//        OLD.txn = txn.value
//        OLD.txnNext = txn.value
//        OLD.action = ACTION_DELETE.toShort()
//        OLD.author = session.context.author ?: session.context.appId
//        if (session.context.author != null) {
//            OLD.authorTs = txnTs
//        }
//        if (OLD.createdAt != null) {
//            OLD.createdAt = OLD.updatedAt
//        }
//        OLD.updatedAt = txnTs
//        OLD.appId = session.context.appId
//        OLD.uid = session.nextUid()
//        val currentVersion: Int = OLD.version ?: 1
//        OLD.version = currentVersion + 1
    }

    /**
     * Create a GRID ([GeoHash](https://en.wikipedia.org/wiki/Geohash) Reference ID) from the given geometry.
     * The GRID is used for distributed processing of features. This method uses GeoHash at level 14, which uses
     * 34 bits for latitude and 36 bits for longitude (70-bit total). The precision is therefore higher than 1mm.
     *
     * If the feature does not have a geometry, this method creates a pseudo GRID (GeoHash Reference ID) from the
     * given feature-id, this is based upon [Geohash](https://en.wikipedia.org/wiki/Geohash#Textual_representation).
     *
     * See [https://www.movable-type.co.uk/scripts/geohash.html](https://www.movable-type.co.uk/scripts/geohash.html)
     * @param id The feature-id.
     * @param flags The geometry type.
     * @param geo The feature geometry; if any.
     * @return The GRID (14 character long string).
     */
    internal fun grid(id: String, flags: Flags, geo: ByteArray?): Int {
        // FIXME TODO use point to here tile function after merge
        return 0
//        if (geo == null) return Static.gridFromId(id)
//        if (!this::gridPlan.isInitialized) {
//            gridPlan = sql.prepare("SELECT ST_GeoHash(ST_Centroid(naksha_geometry($1::int2,$2::bytea)),14) as hash", arrayOf(SQL_INT16, SQL_BYTE_ARRAY))
//        }
//        return asMap(asArray(gridPlan.execute(arrayOf(flags, geo)))[0])["hash"]!!
    }

    /**
     * Calculates hash of row using Fnv1a32 algorithm.
     * Elements of row used to calculate hash (in order): feature, tags, geo, geoRef
     *
     * @param row
     * @return hash
     */
    internal fun rowHash(row: Row): Int {
        var totalHash = Fnv1a32.hashByteArray(row.feature)
        totalHash = Fnv1a32.hashByteArray(row.tags, totalHash)
        totalHash = Fnv1a32.hashByteArray(row.geo, totalHash)
        totalHash = Fnv1a32.hashByteArray(row.geoRef, totalHash)
        return totalHash
    }
}
