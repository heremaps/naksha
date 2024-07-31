package naksha.psql.write

import naksha.base.Fnv1a32
import naksha.base.Platform
import naksha.model.*
import naksha.psql.PgPlan
import naksha.psql.PgRow
import naksha.psql.PgSession
import naksha.psql.TRANSACTIONS_COL

internal class RowUpdater(val session: PgSession) {
    private lateinit var gridPlan: PgPlan

    /**
     * Create the XYZ namespace for an _INSERT_ operation.
     * @param collectionId The collection into which a feature is inserted.
     * @param NEW The row in which to update the XYZ namespace columns.
     * @return The new XYZ namespace for this feature.
     */
    internal fun xyzInsert(collectionId: String, NEW: PgRow) {
        val txn = session.txn()
        val txnTs = session.txnTs()

        var geoGrid: Int? = NEW.geo_grid

        // FIXME: default flags should be taken from collectionConfig
        val flags = NEW.flags ?: Flags()

        if (geoGrid == null) {
            // Only calculate geo-grid, if not given by the client.
            val id: String = NEW.id!!
            geoGrid = grid(id, flags, NEW.geo)
        }

        val uid = if (collectionId == TRANSACTIONS_COL) {
            0
        } else {
            session.nextUid()
        }

        NEW.txn = txn.txn
        NEW.txn_next = null
        NEW.ptxn = null
        NEW.puid = 0
        NEW.geo_grid = geoGrid
        NEW.version = null // saving space null means 1
        NEW.uid = uid
        NEW.created_at = null // saving space - it is same as update_at at creation,
        NEW.updated_at = txnTs
        NEW.author = session.options.author
        NEW.author_ts = null // saving space - only apps are allowed to create features
        NEW.app_id = session.options.appId
        NEW.flags = flags
        NEW.fnva1 = rowHash(NEW)
    }

    /**
     *  Prepares XyzNamespace columns for head table.
     */
    internal fun xyzUpdateHead(collectionId: String, NEW: PgRow, OLD: PgRow) {
        xyzInsert(collectionId, NEW)
        val updatedAt = Platform.currentMillis()
        if (session.options.author == null) {
            NEW.author = OLD.author
            NEW.author_ts = OLD.author_ts
        } else {
            NEW.author = session.options.author
            NEW.author_ts = NEW.updated_at
        }
        NEW.version = (OLD.version ?: 0) + 1
        NEW.ptxn = OLD.txn
        NEW.puid = OLD.uid
        if (collectionId == TRANSACTIONS_COL) {
            NEW.updated_at = updatedAt
        }
    }

    /**
     * Prepares row before putting into $del table.
     */
    internal fun xyzDel(OLD: PgRow) {
        val txn = session.txn()
        val txnTs = session.txnTs()
        OLD.txn = txn.txn
        OLD.txn_next = txn.txn
        OLD.flags = Flags(OLD.flags!!).action(Action.DELETED)
        OLD.author = session.options.author ?: session.options.appId
        if (session.options.author != null) {
            OLD.author_ts = txnTs
        }
        if (OLD.created_at != null) {
            OLD.created_at = OLD.updated_at
        }
        OLD.updated_at = txnTs
        OLD.app_id = session.options.appId
        OLD.uid = session.nextUid()
        val currentVersion: Int = OLD.version ?: 1
        OLD.version = currentVersion + 1
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
    internal fun rowHash(row: PgRow): Int {
        var totalHash = Fnv1a32.hashByteArray(row.feature)
        totalHash = Fnv1a32.hashByteArray(row.tags, totalHash)
        totalHash = Fnv1a32.hashByteArray(row.geo, totalHash)
        totalHash = Fnv1a32.hashByteArray(row.geo_ref, totalHash)
        return totalHash
    }
}
