package naksha.plv8.write

import com.here.naksha.lib.jbon.ACTION_DELETE
import com.here.naksha.lib.jbon.ACTION_UPDATE
import naksha.base.Platform
import naksha.model.Flags
import naksha.model.response.Metadata
import naksha.model.response.Row
import naksha.plv8.IPlv8Plan
import naksha.plv8.NakshaSession
import naksha.plv8.Static.SC_TRANSACTIONS
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class RowUpdater(
    val session: NakshaSession
) {
    private lateinit var gridPlan: IPlv8Plan

    /**
     * Create the XYZ namespace for an _INSERT_ operation.
     * @param collectionId The collection into which a feature is inserted.
     * @param NEW The row in which to update the XYZ namespace columns.
     * @return The new XYZ namespace for this feature.
     */
    internal fun xyzInsert(collectionId: String, NEW: Row) {
        val newMeta = NEW.meta!!
        val txn = session.txn()
        val txnTs = session.txnTs()
        newMeta.txn = txn.value
        newMeta.txnNext = null
        newMeta.ptxn = null
        newMeta.puid = null

        val geoGrid: Int? = newMeta.geoGrid
        if (geoGrid == null) {
            // Only calculate geo-grid, if not given by the client.
            val id: String? = NEW.id
            check(id != null) { "Missing id" }
            newMeta.geoGrid = grid(id, Flags.readGeometryEncoding(newMeta.flags!!), NEW.geo)
        }
        newMeta.action = null // saving space null means 0 (create)
        newMeta.version = null // saving space null means 1
        if (collectionId == SC_TRANSACTIONS) {
            newMeta.uid = 0
        } else {
            newMeta.uid = session.nextUid()
        }
        newMeta.createdAt = null // saving space - it is same as update_at at creation,
        newMeta.updatedAt = txnTs
        newMeta.author = session.author
        newMeta.authorTs = null // saving space - only apps are allowed to create features
        newMeta.appId = session.appId
    }

    /**
     *  Prepares XyzNamespace columns for head table.
     */
    internal fun xyzUpdateHead(collectionId: String, NEW: Row, OLD: Row) {
        xyzInsert(collectionId, NEW)
        val newMeta = NEW.meta!!
        val oldMeta = OLD.meta!!
        newMeta.action = ACTION_UPDATE.toShort()
        newMeta.createdAt = oldMeta.createdAt ?: oldMeta.updatedAt
        if (session.author == null) {
            newMeta.authorTs = oldMeta.authorTs ?: oldMeta.updatedAt
        } else {
            newMeta.authorTs = null
        }
        val oldVersion: Int = oldMeta.version ?: 1
        newMeta.version = oldVersion + 1
        newMeta.ptxn = oldMeta.txn
        newMeta.puid = oldMeta.uid
        if (collectionId == SC_TRANSACTIONS) {
            newMeta.updatedAt = Platform.currentMillis()
        }
    }

    /**
     * Prepares row before putting into $del table.
     */
    internal fun xyzDel(OLD: Metadata) {
        val txn = session.txn()
        val txnTs = session.txnTs()
        OLD.txn = txn.value
        OLD.txnNext = txn.value
        OLD.action = ACTION_DELETE.toShort()
        OLD.author = session.author ?: session.appId
        if (session.author != null) {
            OLD.authorTs = txnTs
        }
        if (OLD.createdAt != null) {
            OLD.createdAt = OLD.updatedAt
        }
        OLD.updatedAt = txnTs
        OLD.appId = session.appId
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
    internal fun grid(id: String, flags: Int, geo: ByteArray?): Int {
        // FIXME TODO use point to here tile function after merge
        return 0
//        if (geo == null) return Static.gridFromId(id)
//        if (!this::gridPlan.isInitialized) {
//            gridPlan = sql.prepare("SELECT ST_GeoHash(ST_Centroid(naksha_geometry($1::int2,$2::bytea)),14) as hash", arrayOf(SQL_INT16, SQL_BYTE_ARRAY))
//        }
//        return asMap(asArray(gridPlan.execute(arrayOf(flags, geo)))[0])["hash"]!!
    }
}