package com.here.naksha.lib.plv8

import NakshaBulkLoaderPlan
import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.NakshaBulkLoaderOp.Companion.mapToOperations
import com.here.naksha.lib.plv8.Static.DEBUG

class NakshaBulkLoader(
        val collectionId: String,
        val session: NakshaSession
) {

    private val headCollectionId = session.getBaseCollectionId(collectionId)
    private val collectionIdQuoted = session.sql.quoteIdent(collectionId)
    private val delCollectionId = "${headCollectionId}\$del"
    private val delCollectionIdQuoted = session.sql.quoteIdent(delCollectionId)
    private val hstCollectionIdQuoted = quotedHst(headCollectionId)

    private fun currentMillis() : BigInt64? = if (DEBUG) Jb.env.currentMicros() / 1000 else null

    fun bulkWriteFeatures(
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?>,
            geo_type_arr: Array<Short>,
            geo_arr: Array<ByteArray?>,
            tags_arr: Array<ByteArray?>
    ) {
        val START = currentMillis()
        val collectionConfig = session.getCollectionConfig(headCollectionId)
        val isCollectionPartitioned: Boolean? = collectionConfig[NKC_PARTITION]
        val isHistoryDisabled: Boolean? = collectionConfig[NKC_DISABLE_HISTORY]

        val START_MAPPING = currentMillis()
        val (allOperations, idsToModify, idsToPurge, partition) = mapToOperations(headCollectionId, op_arr, feature_arr, geo_type_arr, geo_arr, tags_arr)
        val END_MAPPING = currentMillis()

        session.sql.execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")
        val existingFeatures = existingFeatures(headCollectionId, idsToModify)
        val existingInDelFeatures = existingFeatures(delCollectionId, idsToPurge)
        val END_LOADING = currentMillis()

        val START_PREPARE = currentMillis()
        val featureIdsToDeleteFromDel = mutableListOf<String>()
        val featuresToPurgeFromDel = mutableListOf<String>()

        val plan: NakshaBulkLoaderPlan = if (isCollectionPartitioned == true && partition != null) {
            if (DEBUG) println("Insert into a single partition #$partition (isCollectionPartitioned: ${isCollectionPartitioned})")
            NakshaBulkLoaderPlan(partition, getPartitionHeadQuoted(true, partition), delCollectionIdQuoted, hstCollectionIdQuoted, session)
        } else {
            if (DEBUG) println("Insert into a multiple partitions, therefore via HEAD (isCollectionPartitioned: ${isCollectionPartitioned})")
            NakshaBulkLoaderPlan(null, getPartitionHeadQuoted(false, -1), delCollectionIdQuoted, hstCollectionIdQuoted, session)
        }
        for (op in allOperations) {
            val opType = calculateOpToPerform(op, existingFeatures)
            val featureRowMap = op.rowMap
            when (opType) {
                XYZ_OP_CREATE -> {
                    featureIdsToDeleteFromDel.add(op.id)
                    session.xyzInsert(op.collectionId, featureRowMap)
                    addInsertStmt(plan.insertHeadPlan(), featureRowMap)
                }

                XYZ_OP_UPDATE -> {
                    val headBeforeUpdate: IMap = existingFeatures[op.id]!!
                    checkStateForAtomicOp(op.id, op.xyzOp.uuid(), headBeforeUpdate)

                    featureIdsToDeleteFromDel.add(op.id)
                    addCopyHeadToHstStmt(plan.copyHeadToHstPlan(), featureRowMap, isHistoryDisabled)
                    session.xyzUpdateHead(op.collectionId, featureRowMap, headBeforeUpdate)
                    addUpdateHeadStmt(plan.updateHeadPlan(), featureRowMap)
                }

                XYZ_OP_DELETE, XYZ_OP_PURGE -> {
                    if (existingFeatures.containsKey(op.id)) {
                        // this may throw exception (if we try to delete non-existing feature - that was deleted before)
                        val headBeforeDelete: IMap = existingFeatures[op.id]!!
                        checkStateForAtomicOp(op.id, op.xyzOp.uuid(), headBeforeDelete)
                        addCopyHeadToHstStmt(plan.copyHeadToHstPlan(), featureRowMap, isHistoryDisabled)

                        featureRowMap[COL_VERSION] = headBeforeDelete[COL_VERSION]
                        featureRowMap[COL_AUTHOR_TS] = headBeforeDelete[COL_AUTHOR_TS]
                        session.xyzDel(featureRowMap)
                        addDelStmt(plan.insertDelPlan(), featureRowMap)
                        addDeleteHeadStmt(plan.deleteHeadPlan(), featureRowMap)
                        if (isHistoryDisabled == false) addCopyDelToHstStmt(plan.copyDelToHstPlan(), featureRowMap)
                    }
                    if (opType == XYZ_OP_PURGE) {
                        checkStateForAtomicOp(op.id, op.xyzOp.uuid(), existingInDelFeatures[op.id]!!)
                        featuresToPurgeFromDel.add(op.id)
                    }
                }

                else -> throw RuntimeException("Operation $opType not supported")
            }
        }
        val END_PREPARE = currentMillis()

        val START_EXECUTION = currentMillis()
        // 1.
        if (featureIdsToDeleteFromDel.isNotEmpty()) executeBatchDeleteFromDel(delCollectionIdQuoted, featureIdsToDeleteFromDel)
        // 3.
        if (plan.insertDelPlan != null) executeBatch(plan.insertDelPlan())
        // 4. insert to history and update head
        if (plan.copyHeadToHstPlan != null) executeBatch(plan.copyHeadToHstPlan())
        if (plan.updateHeadPlan != null) executeBatch(plan.updateHeadPlan())
        if (plan.deleteHeadPlan != null) executeBatch(plan.deleteHeadPlan())
        // 5. copy del to hst
        if (plan.copyDelToHstPlan != null) executeBatch(plan.copyDelToHstPlan())
        // 6.
        if (plan.insertHeadPlan != null) executeBatch(plan.insertHeadPlan())
        // 7. purge
        if (featuresToPurgeFromDel.isNotEmpty()) executeBatchDeleteFromDel(delCollectionIdQuoted, featuresToPurgeFromDel)
        val END_EXECUTION = currentMillis()

        val END = currentMillis()
        if (DEBUG) {
            println("[${op_arr.size} feature]: ${END!! - START!!}ms, loading: ${END_LOADING!! - START}ms, execution: ${END_EXECUTION!! - START_EXECUTION!!}ms, mapping: ${END_MAPPING!! - START_MAPPING!!}ms, preparing: ${END_PREPARE!! - START_PREPARE!!}ms")
        }
    }

    private fun getPartitionHeadQuoted(isCollectionPartitioned: Boolean?, partitionKey: Int) =
            if (isCollectionPartitioned == true) session.sql.quoteIdent("${headCollectionId}\$p${Static.PARTITION_ID[partitionKey]}") else collectionIdQuoted

    private fun groupByPartition(isCollectionPartitioned: Boolean?, allOperations: List<NakshaBulkLoaderOp>) =
            if (isCollectionPartitioned == true) {
                allOperations.groupBy { it.partition }
            } else {
                allOperations.groupBy { -1 }
            }

    private fun checkStateForAtomicOp(reqId: String, reqUuid: String?, currentHead: IMap) {
        if (reqUuid != null) {
            val headUuid = NakshaUuid.from(session.storageId, collectionId, currentHead[COL_TXN]!!, currentHead[COL_UID]!!)
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

    internal fun executeBatchDeleteFromDel(delCollectionIdQuoted: String, featureIdsToDeleteFromDel: MutableList<String>) {
        if (featureIdsToDeleteFromDel.isNotEmpty()) {
            session.sql.execute("DELETE FROM  $delCollectionIdQuoted WHERE id = ANY($1)", arrayOf(featureIdsToDeleteFromDel.toTypedArray()))
        }
    }

    internal fun calculateOpToPerform(row: NakshaBulkLoaderOp, existingFeatures: IMap): Int {
        return if (row.xyzOp.op() == XYZ_OP_UPSERT) {
            if (existingFeatures.containsKey(row.id)) {
                XYZ_OP_UPDATE
            } else {
                XYZ_OP_CREATE
            }
        } else {
            row.xyzOp.op()
        }
    }

    fun addDelStmt(plan: IPlv8Plan, row: IMap) {
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

    fun addCopyDelToHstStmt(stmt: IPlv8Plan, row: IMap) {
        stmt.setString(1, row[COL_ID])
        stmt.addBatch()
    }

    fun addInsertStmt(stmt: IPlv8Plan, row: IMap) {
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

    fun addUpdateHeadStmt(stmt: IPlv8Plan, row: IMap) {
        setAllColumnsOnStmt(stmt, row)
        stmt.setString(21, row[COL_ID])
        stmt.addBatch()
    }

    fun addDeleteHeadStmt(stmt: IPlv8Plan, row: IMap) {
        stmt.setString(1, row[COL_ID])
        stmt.addBatch()
    }

    fun addCopyHeadToHstStmt(stmt: IPlv8Plan, row: IMap, isHstDisabled: Boolean?) {
        if (isHstDisabled == false) {
            stmt.setLong(1, session.txn().value)
            stmt.setString(2, row[COL_ID])
            stmt.addBatch()
        }
    }

    internal fun setAllColumnsOnStmt(stmt: IPlv8Plan, row: IMap) {
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

    internal fun quotedHst(collectionHeadId: String) = session.sql.quoteIdent("${collectionHeadId}\$hst")

    internal fun executeBatch(stmt: IPlv8Plan) {
        val result = stmt.executeBatch()
        if (result.isNotEmpty() && result[0] == -3) {
            // java.sql.Statement.EXECUTE_FAILED
            throw NakshaException.forBulk(ERR_FATAL, "error in bulk statement")
        }
    }

    internal fun existingFeatures(collectionId: String, ids: List<String>) = if (ids.isNotEmpty()) {
        session.queryForExisting(collectionId, ids, wait = false)
    } else {
        newMap()
    }

}