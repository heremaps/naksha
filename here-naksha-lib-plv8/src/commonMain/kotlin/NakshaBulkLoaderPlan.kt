import com.here.naksha.lib.jbon.IMap
import com.here.naksha.lib.jbon.NakshaUuid
import com.here.naksha.lib.jbon.SQL_BYTE_ARRAY
import com.here.naksha.lib.jbon.SQL_INT16
import com.here.naksha.lib.jbon.SQL_INT32
import com.here.naksha.lib.jbon.SQL_INT64
import com.here.naksha.lib.jbon.SQL_STRING
import com.here.naksha.lib.jbon.get
import com.here.naksha.lib.jbon.plus
import com.here.naksha.lib.jbon.set
import com.here.naksha.lib.plv8.COL_ACTION
import com.here.naksha.lib.plv8.COL_ALL
import com.here.naksha.lib.plv8.COL_ALL_TYPES
import com.here.naksha.lib.plv8.COL_APP_ID
import com.here.naksha.lib.plv8.COL_AUTHOR
import com.here.naksha.lib.plv8.COL_AUTHOR_TS
import com.here.naksha.lib.plv8.COL_CREATED_AT
import com.here.naksha.lib.plv8.COL_FEATURE
import com.here.naksha.lib.plv8.COL_GEOMETRY
import com.here.naksha.lib.plv8.COL_GEO_GRID
import com.here.naksha.lib.plv8.COL_GEO_REF
import com.here.naksha.lib.plv8.COL_FLAGS
import com.here.naksha.lib.plv8.COL_ID
import com.here.naksha.lib.plv8.COL_PTXN
import com.here.naksha.lib.plv8.COL_PUID
import com.here.naksha.lib.plv8.COL_TAGS
import com.here.naksha.lib.plv8.COL_TXN
import com.here.naksha.lib.plv8.COL_TXN_NEXT
import com.here.naksha.lib.plv8.COL_TYPE
import com.here.naksha.lib.plv8.COL_UID
import com.here.naksha.lib.plv8.COL_UPDATE_AT
import com.here.naksha.lib.plv8.COL_VERSION
import com.here.naksha.lib.plv8.ERR_CHECK_VIOLATION
import com.here.naksha.lib.plv8.ERR_FATAL
import com.here.naksha.lib.plv8.IPlv8Plan
import com.here.naksha.lib.plv8.NakshaException
import com.here.naksha.lib.plv8.NakshaRequestOp
import com.here.naksha.lib.plv8.NakshaSession
import com.here.naksha.lib.plv8.Param
import kotlin.reflect.KFunction0

internal class NakshaBulkLoaderPlan(
        val collectionId: String,
        val partitionHeadQuoted: String,
        val session: NakshaSession,
        val isHistoryDisabled: Boolean?,
        val autoPurge: Boolean,
        val minResult: Boolean) {

    private val headCollectionId = session.getBaseCollectionId(collectionId)
    private val delCollectionId = "${headCollectionId}\$del"
    private val delCollectionIdQuoted = session.sql.quoteIdent(delCollectionId)
    private val hstCollectionIdQuoted = session.sql.quoteIdent("${headCollectionId}\$hst")

    internal val featureIdsToDeleteFromDel = mutableListOf<String>()
    internal val featuresToPurgeFromDel = mutableListOf<String>()
    internal val result = session.sql.newTable()

    internal val insertToHeadBulkParams = mutableListOf<Array<Param>>()
    internal val copyHeadToDelBulkParams = mutableListOf<Array<Param>>()
    internal val insertDelToHstBulkParams = mutableListOf<Array<Param>>()
    internal val updateHeadBulkParams = mutableListOf<Array<Param>>()
    internal val deleteHeadBulkParams = mutableListOf<Array<Param>>()
    internal val copyHeadToHstBulkParams = mutableListOf<Array<Param>>()

    private fun insertHeadPlan(): IPlv8Plan {
        return session.sql.prepare("""INSERT INTO $partitionHeadQuoted (
                $COL_CREATED_AT,$COL_UPDATE_AT,$COL_TXN,$COL_UID,$COL_GEO_GRID,$COL_FLAGS,
                $COL_APP_ID,$COL_AUTHOR,$COL_TYPE,$COL_ID,
                $COL_FEATURE,$COL_TAGS,$COL_GEOMETRY,$COL_GEO_REF)
                VALUES($1,$2,$3,$4,$5,
                $6,$7,$8,$9,
                $10,$11,$12,$13,$14)""".trimIndent(),
                arrayOf(SQL_INT64, SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT32, SQL_INT32,
                        SQL_STRING, SQL_STRING, SQL_STRING, SQL_STRING,
                        SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY))
    }

    private fun updateHeadPlan(): IPlv8Plan {
        return session.sql.prepare("""
                UPDATE $partitionHeadQuoted 
                SET $COL_TXN_NEXT=$1, $COL_TXN=$2, $COL_UID=$3, $COL_PTXN=$4,$COL_PUID=$5,$COL_FLAGS=$6,$COL_ACTION=$7,$COL_VERSION=$8,$COL_CREATED_AT=$9,$COL_UPDATE_AT=$10,$COL_AUTHOR_TS=$11,$COL_AUTHOR=$12,$COL_APP_ID=$13,$COL_GEO_GRID=$14,$COL_ID=$15,$COL_TAGS=$16,$COL_GEOMETRY=$17,$COL_FEATURE=$18,$COL_GEO_REF=$19,$COL_TYPE=$20 WHERE $COL_ID=$21
                """.trimIndent(),
                arrayOf(*COL_ALL_TYPES, SQL_STRING))
    }

    private fun deleteHeadPlan(): IPlv8Plan {
        return session.sql.prepare("""
                DELETE FROM $partitionHeadQuoted
                WHERE $COL_ID = $1
                """.trimIndent(),
                arrayOf(SQL_STRING))
    }

    private fun insertDelPlan(): IPlv8Plan {
        // ptxn + puid = txn + uid (as we generate new state in _del)
        return session.sql.prepare("""
                INSERT INTO $delCollectionIdQuoted ($COL_ALL) 
                SELECT $1,$2,$3,$COL_TXN,$COL_UID,$COL_FLAGS,$4,$5,$6,$7,$8,$9,$10,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE 
                    FROM $partitionHeadQuoted WHERE $COL_ID = $11""".trimIndent(),
                arrayOf(SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT16, SQL_INT32, SQL_INT64, SQL_INT64, SQL_INT64, SQL_STRING, SQL_STRING, SQL_STRING))
    }

    private fun insertDelToHstPlan(): IPlv8Plan {
        return session.sql.prepare("""
                INSERT INTO $hstCollectionIdQuoted ($COL_ALL) 
                SELECT $1,$2,$3,$COL_TXN,$COL_UID,$COL_FLAGS,$4,$5,$6,$7,$8,$9,$10,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE 
                    FROM $partitionHeadQuoted WHERE $COL_ID = $11""".trimIndent(),
                arrayOf(SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT16, SQL_INT32, SQL_INT64, SQL_INT64, SQL_INT64, SQL_STRING, SQL_STRING, SQL_STRING))
    }

    private fun copyHeadToHstPlan(): IPlv8Plan {
        return session.sql.prepare("""
            INSERT INTO $hstCollectionIdQuoted ($COL_ALL) 
            SELECT $1,$COL_TXN,$COL_UID,$COL_PTXN,$COL_PUID,$COL_FLAGS,$COL_ACTION,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR_TS,$COL_AUTHOR,$COL_APP_ID,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE 
                FROM $partitionHeadQuoted WHERE $COL_ID = $2
            """.trimIndent(), arrayOf(SQL_INT64, SQL_STRING))
    }

    fun addCreate(op: NakshaRequestOp) {
        addToRemoveFromDel(op.id)
        session.xyzInsert(op.collectionId, op.rowMap)
        addInsertParams(op.rowMap)
        if (!minResult) {
            result.returnCreated(op.id, session.xyzNsFromRow(collectionId, op.rowMap))
        }
    }

    fun addUpdate(op: NakshaRequestOp, existingFeature: IMap?) {
        val featureRowMap = op.rowMap
        val headBeforeUpdate: IMap = existingFeature!!
        checkStateForAtomicOp(op.xyzOp.uuid(), headBeforeUpdate)

        addToRemoveFromDel(op.id)
        addCopyHeadToHstParams(featureRowMap, isHistoryDisabled)
        session.xyzUpdateHead(op.collectionId, featureRowMap, headBeforeUpdate)
        addUpdateHeadParams(featureRowMap)
        if (!minResult) {
            result.returnUpdated(op.id, session.xyzNsFromRow(collectionId, headBeforeUpdate.plus(featureRowMap)))
        }
    }

    private fun addToRemoveFromDel(id: String) {
        if (!autoPurge) // it's not in $del, so we can skip query
            featureIdsToDeleteFromDel.add(id)
    }

    fun addDelete(op: NakshaRequestOp, existingFeature: IMap?) {
        addDeleteInternal(op, existingFeature)
        if (!minResult) {
            if (existingFeature == null) {
                result.returnRetained(op.id)
            } else {
                val headBeforeDelete: IMap = existingFeature
                result.returnDeleted(headBeforeDelete, session.xyzNsFromRow(collectionId, headBeforeDelete + op.rowMap))
            }
        }
    }

    fun addPurge(op: NakshaRequestOp, existingFeature: IMap?, existingInDelFeature: IMap?) {
        addDeleteInternal(op, existingFeature)
        val deletedFeatureRow: IMap? = existingInDelFeature ?: existingFeature
        checkStateForAtomicOp(op.xyzOp.uuid(), deletedFeatureRow)
        if (!autoPurge) // it's not in $del, so we don't have to purge
            featuresToPurgeFromDel.add(op.id)
        if (!minResult) {
            if (deletedFeatureRow == null) {
                result.returnRetained(op.id)
            } else {
                result.returnPurged(deletedFeatureRow, session.xyzNsFromRow(collectionId, deletedFeatureRow))
            }
        }
    }

    internal fun executeAll() {
        // 1.
        executeBatchDeleteFromDel(featureIdsToDeleteFromDel)
        // 3.
        executeBatch(::insertDelPlan, copyHeadToDelBulkParams)
        // 4. insert to history and update head
        executeBatch(::copyHeadToHstPlan, copyHeadToHstBulkParams)
        executeBatch(::updateHeadPlan, updateHeadBulkParams)
        // 5. copy head (del state) to hst
        executeBatch(::insertDelToHstPlan, insertDelToHstBulkParams)
        executeBatch(::deleteHeadPlan, deleteHeadBulkParams)
        // 6.
        executeBatch(::insertHeadPlan, insertToHeadBulkParams)
        // 7. purge
        executeBatchDeleteFromDel(featuresToPurgeFromDel)
    }

    private fun addDeleteInternal(op: NakshaRequestOp, existingFeature: IMap?) {
        val featureRowMap = op.rowMap
        if (existingFeature != null) {
            // this may throw exception (if we try to delete non-existing feature - that was deleted before)
            val headBeforeDelete: IMap = existingFeature
            checkStateForAtomicOp(op.xyzOp.uuid(), headBeforeDelete)
            addCopyHeadToHstParams(featureRowMap, isHistoryDisabled)

            featureRowMap[COL_VERSION] = headBeforeDelete[COL_VERSION]
            featureRowMap[COL_AUTHOR_TS] = headBeforeDelete[COL_AUTHOR_TS]
            featureRowMap[COL_UPDATE_AT] = headBeforeDelete[COL_UPDATE_AT]
            featureRowMap[COL_CREATED_AT] = headBeforeDelete[COL_CREATED_AT]
            session.xyzDel(featureRowMap)
            if (!autoPurge) // do not push to del
                addDelParams(copyHeadToDelBulkParams, featureRowMap)
            addDeleteHeadParams(featureRowMap)
            if (isHistoryDisabled == false) // even if it's autoPurge we still need a deleted copy in $hst if it's enabled.
                addDelParams(insertDelToHstBulkParams, featureRowMap)
        }
    }

    private fun addInsertParams(row: IMap) {
        // created_at = NULL
        insertToHeadBulkParams.add(arrayOf(
                Param(1, SQL_INT64, row[COL_CREATED_AT]),
                Param(2, SQL_INT64, row[COL_UPDATE_AT]),
                Param(3, SQL_INT64, row[COL_TXN]),
                Param(4, SQL_INT32, row[COL_UID]),
                Param(5, SQL_INT32, row[COL_GEO_GRID]),
                Param(6, SQL_INT32, row[COL_FLAGS]),
                Param(7, SQL_STRING, row[COL_APP_ID]),
                Param(8, SQL_STRING, row[COL_AUTHOR]),
                Param(9, SQL_STRING, row[COL_TYPE]),
                Param(10, SQL_STRING, row[COL_ID]),
                Param(11, SQL_BYTE_ARRAY, row[COL_FEATURE]),
                Param(12, SQL_BYTE_ARRAY, row[COL_TAGS]),
                Param(13, SQL_BYTE_ARRAY, row[COL_GEOMETRY]),
                Param(14, SQL_BYTE_ARRAY, row[COL_GEO_REF]),
        ))
    }

    private fun addDelParams(params: MutableList<Array<Param>>, row :IMap) {
        params.add(arrayOf(
                Param(1, SQL_INT64, row[COL_TXN_NEXT]),
                Param(2, SQL_INT64, row[COL_TXN]),
                Param(3, SQL_INT32, row[COL_UID]),
                Param(4, SQL_INT16, row[COL_ACTION]),
                Param(5, SQL_INT32, row[COL_VERSION]),
                Param(6, SQL_INT64, row[COL_CREATED_AT]),
                Param(7, SQL_INT64, row[COL_UPDATE_AT]),
                Param(8, SQL_INT64, row[COL_AUTHOR_TS]),
                Param(9, SQL_STRING, row[COL_AUTHOR]),
                Param(10, SQL_STRING, row[COL_APP_ID]),
                Param(11, SQL_STRING, row[COL_ID])
        ))
    }

    private fun addUpdateHeadParams(row: IMap) {
        updateHeadBulkParams.add(arrayOf(
                Param(1, SQL_INT64, row[COL_TXN_NEXT]),
                Param(2, SQL_INT64, row[COL_TXN]),
                Param(3, SQL_INT32, row[COL_UID]),
                Param(4, SQL_INT64, row[COL_PTXN]),
                Param(5, SQL_INT32, row[COL_PUID]),
                Param(6, SQL_INT32, row[COL_FLAGS]),
                Param(7, SQL_INT16, row[COL_ACTION]),
                Param(8, SQL_INT32, row[COL_VERSION]),
                Param(9, SQL_INT64, row[COL_CREATED_AT]),
                Param(10, SQL_INT64, row[COL_UPDATE_AT]),
                Param(11, SQL_INT64, row[COL_AUTHOR_TS]),
                Param(12, SQL_STRING, row[COL_AUTHOR]),
                Param(13, SQL_STRING, row[COL_APP_ID]),
                Param(14, SQL_INT32, row[COL_GEO_GRID]),
                Param(15, SQL_STRING, row[COL_ID]),
                Param(16, SQL_BYTE_ARRAY, row[COL_TAGS]),
                Param(17, SQL_BYTE_ARRAY, row[COL_GEOMETRY]),
                Param(18, SQL_BYTE_ARRAY, row[COL_FEATURE]),
                Param(19, SQL_BYTE_ARRAY, row[COL_GEO_REF]),
                Param(20, SQL_STRING, row[COL_TYPE]),
                Param(21, SQL_STRING, row[COL_ID])
        ))
    }

    private fun addDeleteHeadParams(row: IMap) {
        deleteHeadBulkParams.add(arrayOf(
                Param(1, SQL_STRING, row[COL_ID])
        ))
    }

    private fun addCopyHeadToHstParams(row: IMap, isHstDisabled: Boolean?) {
        if (isHstDisabled == false) {
            copyHeadToHstBulkParams.add(arrayOf(
                    Param(1, SQL_INT64, session.txn().value),
                    Param(2, SQL_STRING, row[COL_ID]),
            ))
        }
    }

    internal fun executeBatchDeleteFromDel(featureIdsToDeleteFromDel: MutableList<String>) {
        if (featureIdsToDeleteFromDel.isNotEmpty()) {
            session.sql.execute("DELETE FROM  $delCollectionIdQuoted WHERE id = ANY($1)", arrayOf(featureIdsToDeleteFromDel.toTypedArray()))
        }
    }

    internal fun executeBatch(stmt: KFunction0<IPlv8Plan>, bulkParams: List<Array<Param>>) {
        if (bulkParams.isNotEmpty()) {
            val result = session.sql.executeBatch(stmt(), bulkParams.toTypedArray())
            if (result.isNotEmpty() && result[0] == -3) {
                // java.sql.Statement.EXECUTE_FAILED
                throw NakshaException.forBulk(ERR_FATAL, "error in bulk statement")
            }
        }
    }

    private fun checkStateForAtomicOp(reqUuid: String?, currentHead: IMap?) {
        if (reqUuid != null) {
            val headUuid = NakshaUuid.from(session.storageId, collectionId, currentHead!![COL_TXN]!!, currentHead[COL_UID]!!)
            val expectedUuid = NakshaUuid.fromString(reqUuid)
            if (expectedUuid != headUuid) {
                throw NakshaException.forId(
                        ERR_CHECK_VIOLATION,
                        "Atomic operation for $reqUuid is impossible, expected state: $reqUuid, actual: $headUuid",
                        reqUuid
                )
            }
        }
    }
}