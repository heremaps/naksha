import com.here.naksha.lib.jbon.IMap
import com.here.naksha.lib.jbon.NakshaUuid
import com.here.naksha.lib.jbon.SQL_BYTE_ARRAY
import com.here.naksha.lib.jbon.SQL_INT16
import com.here.naksha.lib.jbon.SQL_INT32
import com.here.naksha.lib.jbon.SQL_INT64
import com.here.naksha.lib.jbon.SQL_STRING
import com.here.naksha.lib.jbon.containsKey
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
import com.here.naksha.lib.plv8.COL_GEO_TYPE
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

internal class NakshaBulkLoaderPlan(
        val collectionId: String,
        val partitionHeadQuoted: String,
        val session: NakshaSession,
        val isHistoryDisabled: Boolean?,
        val minResult: Boolean) {

    private val headCollectionId = session.getBaseCollectionId(collectionId)
    private val delCollectionId = "${headCollectionId}\$del"
    private val delCollectionIdQuoted = session.sql.quoteIdent(delCollectionId)
    private val hstCollectionIdQuoted = session.sql.quoteIdent("${headCollectionId}\$hst")

    internal val featureIdsToDeleteFromDel = mutableListOf<String>()
    internal val featuresToPurgeFromDel = mutableListOf<String>()
    internal val result = session.sql.newTable()

    internal var insertHeadPlan: IPlv8Plan? = null
    internal var updateHeadPlan: IPlv8Plan? = null
    internal var deleteHeadPlan: IPlv8Plan? = null
    internal var insertDelPlan: IPlv8Plan? = null
    internal var copyHeadToHstPlan: IPlv8Plan? = null
    internal var copyDelToHstPlan: IPlv8Plan? = null

    private fun insertHeadPlan(): IPlv8Plan {
        if (insertHeadPlan == null) {
            insertHeadPlan = session.sql.prepare("""INSERT INTO $partitionHeadQuoted (
                $COL_UPDATE_AT,$COL_TXN,$COL_UID,$COL_GEO_GRID,$COL_GEO_TYPE,
                $COL_APP_ID,$COL_AUTHOR,$COL_TYPE,$COL_ID,
                $COL_FEATURE,$COL_TAGS,$COL_GEOMETRY,$COL_GEO_REF)
                VALUES($1,$2,$3,$4,$5,
                $6,$7,$8,$9,
                $10,$11,$12,$13)""".trimIndent(),
                    arrayOf(SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT32, SQL_INT16,
                            SQL_STRING, SQL_STRING, SQL_STRING, SQL_STRING,
                            SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY))
        }
        return insertHeadPlan!!
    }

    private fun updateHeadPlan(): IPlv8Plan {
        if (updateHeadPlan == null) {
            updateHeadPlan = session.sql.prepare("""
                UPDATE $partitionHeadQuoted 
                SET $COL_TXN_NEXT=$1, $COL_TXN=$2, $COL_UID=$3, $COL_PTXN=$4,$COL_PUID=$5,$COL_GEO_TYPE=$6,$COL_ACTION=$7,$COL_VERSION=$8,$COL_CREATED_AT=$9,$COL_UPDATE_AT=$10,$COL_AUTHOR_TS=$11,$COL_AUTHOR=$12,$COL_APP_ID=$13,$COL_GEO_GRID=$14,$COL_ID=$15,$COL_TAGS=$16,$COL_GEOMETRY=$17,$COL_FEATURE=$18,$COL_GEO_REF=$19,$COL_TYPE=$20 WHERE $COL_ID=$21
                """.trimIndent(),
                    arrayOf(*COL_ALL_TYPES, SQL_STRING))
        }
        return updateHeadPlan!!
    }

    private fun deleteHeadPlan(): IPlv8Plan {
        if (deleteHeadPlan == null) {
            deleteHeadPlan = session.sql.prepare("""
                DELETE FROM $partitionHeadQuoted
                WHERE $COL_ID = $1
                """.trimIndent(),
                    arrayOf(SQL_STRING))
        }
        return deleteHeadPlan!!
    }

    private fun insertDelPlan(): IPlv8Plan {
        if (insertDelPlan == null) {
            // ptxn + puid = txn + uid (as we generate new state in _del)
            insertDelPlan = session.sql.prepare("""
                INSERT INTO $delCollectionIdQuoted ($COL_ALL) 
                SELECT $1,$2,$3,$COL_TXN,$COL_UID,$COL_GEO_TYPE,$4,$5,$COL_CREATED_AT,$5,$6,$7,$8,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE 
                    FROM $partitionHeadQuoted WHERE $COL_ID = $8""".trimIndent(),
                    arrayOf(SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT16, SQL_INT32, SQL_INT64, SQL_INT64, SQL_STRING, SQL_STRING, SQL_STRING))
        }
        return insertDelPlan!!
    }

    private fun copyHeadToHstPlan(): IPlv8Plan {
        if (copyHeadToHstPlan == null) {
            copyHeadToHstPlan = session.sql.prepare("""
            INSERT INTO $hstCollectionIdQuoted ($COL_ALL) 
            SELECT $1,$COL_TXN,$COL_UID,$COL_PTXN,$COL_PUID,$COL_GEO_TYPE,$COL_ACTION,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR_TS,$COL_AUTHOR,$COL_APP_ID,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE 
                FROM $partitionHeadQuoted WHERE $COL_ID = $2
            """.trimIndent(), arrayOf(SQL_INT64, SQL_STRING))
        }
        return copyHeadToHstPlan!!
    }

    private fun copyDelToHstPlan(): IPlv8Plan {
        if (copyDelToHstPlan == null) {
            copyDelToHstPlan = session.sql.prepare("INSERT INTO $hstCollectionIdQuoted ($COL_ALL) SELECT $COL_ALL FROM $delCollectionIdQuoted WHERE $COL_ID = $1", arrayOf(SQL_STRING))
        }
        return copyDelToHstPlan!!
    }

    fun addCreate(op: NakshaRequestOp) {
        featureIdsToDeleteFromDel.add(op.id)
        session.xyzInsert(op.collectionId, op.rowMap)
        addInsertStmt(insertHeadPlan(), op.rowMap)
        if (!minResult) {
            result.returnCreated(op.id, session.xyzNsFromRow(collectionId, op.rowMap))
        }
    }

    fun addUpdate(op: NakshaRequestOp, existingFeature: IMap?) {
        val featureRowMap = op.rowMap
        val headBeforeUpdate: IMap = existingFeature!!
        checkStateForAtomicOp(op.xyzOp.uuid(), headBeforeUpdate)

        featureIdsToDeleteFromDel.add(op.id)
        addCopyHeadToHstStmt(copyHeadToHstPlan(), featureRowMap, isHistoryDisabled)
        session.xyzUpdateHead(op.collectionId, featureRowMap, headBeforeUpdate)
        addUpdateHeadStmt(updateHeadPlan(), featureRowMap)
        if (!minResult) {
            result.returnUpdated(op.id, session.xyzNsFromRow(collectionId, headBeforeUpdate.plus(featureRowMap)))
        }
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
        // FIXME make it better for auto-purge, there is no point of putting row to $del just to remove it in next query
        val deletedFeatureRow: IMap? = existingInDelFeature ?: existingFeature
        checkStateForAtomicOp(op.xyzOp.uuid(), deletedFeatureRow)
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
        executeBatch(insertDelPlan)
        // 4. insert to history and update head
        executeBatch(copyHeadToHstPlan)
        executeBatch(updateHeadPlan)
        executeBatch(deleteHeadPlan)
        // 5. copy del to hst
        executeBatch(copyDelToHstPlan)
        // 6.
        executeBatch(insertHeadPlan)
        // 7. purge
        executeBatchDeleteFromDel(featuresToPurgeFromDel)
    }

    private fun addInsertStmt(stmt: IPlv8Plan, row: IMap) {
        // created_at = NULL
        stmt.setLong(1, row[COL_UPDATE_AT])
        // author_ts = NULL
        stmt.setLong(2, row[COL_TXN])
        // ptxn = NULL
        stmt.setInt(3, row[COL_UID])
        // puid = NULL
        // version = NULL
        stmt.setInt(4, row[COL_GEO_GRID])
        stmt.setShort(5, row[COL_GEO_TYPE])
        // action = NULL

        stmt.setString(6, row[COL_APP_ID])
        stmt.setString(7, row[COL_AUTHOR])
        stmt.setString(8, row[COL_TYPE])
        stmt.setString(9, row[COL_ID])

        stmt.setBytes(10, row[COL_FEATURE])
        stmt.setBytes(11, row[COL_TAGS])
        stmt.setBytes(12, row[COL_GEOMETRY])
        stmt.setBytes(13, row[COL_GEO_REF])
        stmt.addBatch()
    }

    private fun addDeleteInternal(op: NakshaRequestOp, existingFeature: IMap?) {
        val featureRowMap = op.rowMap
        if (existingFeature != null) {
            // this may throw exception (if we try to delete non-existing feature - that was deleted before)
            val headBeforeDelete: IMap = existingFeature
            checkStateForAtomicOp(op.xyzOp.uuid(), headBeforeDelete)
            addCopyHeadToHstStmt(copyHeadToHstPlan(), featureRowMap, isHistoryDisabled)

            featureRowMap[COL_VERSION] = headBeforeDelete[COL_VERSION]
            featureRowMap[COL_AUTHOR_TS] = headBeforeDelete[COL_AUTHOR_TS]
            session.xyzDel(featureRowMap)
            addDelStmt(insertDelPlan(), featureRowMap)
            addDeleteHeadStmt(deleteHeadPlan(), featureRowMap)
            if (isHistoryDisabled == false) addCopyDelToHstStmt(copyDelToHstPlan(), featureRowMap)
        }
    }

    private fun addDelStmt(plan: IPlv8Plan, row: IMap) {
        plan.setLong(1, row[COL_TXN_NEXT])
        plan.setLong(2, row[COL_TXN])
        plan.setInt(3, row[COL_UID])
        plan.setShort(4, row[COL_ACTION])
        plan.setInt(5, row[COL_VERSION])
        plan.setLong(6, row[COL_UPDATE_AT])
        plan.setLong(7, row[COL_AUTHOR_TS])
        plan.setString(8, row[COL_AUTHOR])
        plan.setString(9, row[COL_APP_ID])
        plan.setString(10, row[COL_ID])
        plan.addBatch()
    }

    private fun addCopyDelToHstStmt(stmt: IPlv8Plan, row: IMap) {
        stmt.setString(1, row[COL_ID])
        stmt.addBatch()
    }

    private fun addUpdateHeadStmt(stmt: IPlv8Plan, row: IMap) {
        setAllColumnsOnStmt(stmt, row)
        stmt.setString(21, row[COL_ID])
        stmt.addBatch()
    }

    private fun addDeleteHeadStmt(stmt: IPlv8Plan, row: IMap) {
        stmt.setString(1, row[COL_ID])
        stmt.addBatch()
    }

    private fun addCopyHeadToHstStmt(stmt: IPlv8Plan, row: IMap, isHstDisabled: Boolean?) {
        if (isHstDisabled == false) {
            stmt.setLong(1, session.txn().value)
            stmt.setString(2, row[COL_ID])
            stmt.addBatch()
        }
    }

    private fun setAllColumnsOnStmt(stmt: IPlv8Plan, row: IMap) {
        stmt.setLong(1, row[COL_TXN_NEXT])
        stmt.setLong(2, row[COL_TXN])
        stmt.setInt(3, row[COL_UID])
        stmt.setLong(4, row[COL_PTXN])
        stmt.setInt(5, row[COL_PUID])
        stmt.setShort(6, row[COL_GEO_TYPE])
        stmt.setShort(7, row[COL_ACTION])
        stmt.setInt(8, row[COL_VERSION])
        stmt.setLong(9, row[COL_CREATED_AT])
        stmt.setLong(10, row[COL_UPDATE_AT])
        stmt.setLong(11, row[COL_AUTHOR_TS])
        stmt.setString(12, row[COL_AUTHOR])
        stmt.setString(13, row[COL_APP_ID])
        stmt.setInt(14, row[COL_GEO_GRID])
        stmt.setString(15, row[COL_ID])
        stmt.setBytes(16, row[COL_TAGS])
        stmt.setBytes(17, row[COL_GEOMETRY])
        stmt.setBytes(18, row[COL_FEATURE])
        stmt.setBytes(19, row[COL_GEO_REF])
        stmt.setString(20, row[COL_TYPE])
    }

    internal fun executeBatchDeleteFromDel(featureIdsToDeleteFromDel: MutableList<String>) {
        if (featureIdsToDeleteFromDel.isNotEmpty()) {
            session.sql.execute("DELETE FROM  $delCollectionIdQuoted WHERE id = ANY($1)", arrayOf(featureIdsToDeleteFromDel.toTypedArray()))
        }
    }

    internal fun executeBatch(stmt: IPlv8Plan?) {
        if (stmt != null) {
            val result = stmt.executeBatch()
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