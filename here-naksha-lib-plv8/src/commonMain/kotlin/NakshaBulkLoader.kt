package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.FeatureRow.Companion.mapToFeatureRow

class NakshaBulkLoader(
        val collectionId: String,
        val session: NakshaSession
) {

    private val headCollectionId = session.getBaseCollectionId(collectionId)
    private val collectionIdQuoted = session.sql.quoteIdent(collectionId)
    private val delCollectionIdQuoted = quotedDel(headCollectionId)
    private val hstCollectionIdQuoted = quotedHst(headCollectionId)


    fun bulkWriteFeatures(
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?>,
            geo_type_arr: Array<Short>,
            geo_arr: Array<ByteArray?>,
            tags_arr: Array<ByteArray?>
    ) {

        val collectionConfig = session.getCollectionConfig(headCollectionId)
        val isCollectionPartitioned: Boolean? = collectionConfig[NKC_PARTITION]
        val isHistoryDisabled: Boolean? = collectionConfig[NKC_DISABLE_HISTORY]

        val (allOperations, idsToModify) = mapToFeatureRow(headCollectionId, op_arr, feature_arr, geo_type_arr, geo_arr, tags_arr)

        session.sql.execute("SET LOCAL session_replication_role = replica;")
        val existingFeatures = existingFeatures(idsToModify)

        val featureIdsToDeleteFromDel = mutableListOf<String>()
        val featuresToPurgeFromDel = mutableListOf<String>()

        val partitionOperations = groupByPartition(isCollectionPartitioned, allOperations)
        val orderedPartitionKeys = partitionOperations.keys.sorted()
        for (partitionKey in orderedPartitionKeys) {
            val operations = partitionOperations[partitionKey]!!.sortedBy { it.id() }

            val partitionHeadQuoted = getPartitionHeadQuoted(isCollectionPartitioned, partitionKey)
            val plans = PartitionPlans(partitionHeadQuoted, delCollectionIdQuoted, hstCollectionIdQuoted, session)

            for (row in operations) {
                val op = calculateOpToPerform(row, existingFeatures)
                val featureRowMap = row.rowMap

                when (op) {
                    XYZ_OP_UPDATE -> {
                        val headBeforeUpdate: IMap = existingFeatures[row.id()]!!
                        featureIdsToDeleteFromDel.add(row.id())
                        addCopyHeadToHstStmt(plans.copyHeadToHstPlan, featureRowMap, isHistoryDisabled)
                        session.xyzUpdateHead(row.collectionId, featureRowMap, headBeforeUpdate)
                        addUpdateHeadStmt(plans.updateHeadPlan, featureRowMap)
                    }

                    XYZ_OP_CREATE -> {
                        featureIdsToDeleteFromDel.add(row.id())
                        session.xyzInsert(row.collectionId, featureRowMap)
                        addInsertStmt(plans.insertHeadPlan, featureRowMap)
                    }

                    XYZ_OP_DELETE, XYZ_OP_PURGE -> {
                        if (existingFeatures.containsKey(row.id())) {
                            // this may throw exception (if we try to delete non-existing feature - that was deleted before)
                            val headBeforeDelete: IMap = existingFeatures[row.id()]!!
                            addCopyHeadToHstStmt(plans.copyHeadToHstPlan, featureRowMap, isHistoryDisabled)

                            featureRowMap[COL_VERSION] = headBeforeDelete[COL_VERSION]
                            featureRowMap[COL_AUTHOR_TS] = headBeforeDelete[COL_AUTHOR_TS]
                            session.xyzDel(featureRowMap)
                            addDelStmt(plans.insertDelPlan, featureRowMap)
                            if (isHistoryDisabled == false) addCopyDelToHstStmt(plans.copyDelToHstPlan, featureRowMap)
                        }
                        if (op == XYZ_OP_PURGE)
                            featuresToPurgeFromDel.add(row.id())
                    }

                    else -> throw RuntimeException("Operation $op not supported")
                }
            }

            // 1.
            executeBatchDeleteFromDel(delCollectionIdQuoted, featureIdsToDeleteFromDel)
            // 3.
            executeBatch(plans.insertDelPlan)
            // 4. insert to history and update head
            executeBatch(plans.copyHeadToHstPlan)
            executeBatch(plans.updateHeadPlan)
            // 5. copy del to hst
            executeBatch(plans.copyDelToHstPlan)
            // 6.
            executeBatch(plans.insertHeadPlan)
            // 7. purge
            executeBatchDeleteFromDel(delCollectionIdQuoted, featuresToPurgeFromDel)
        }
    }

    private fun getPartitionHeadQuoted(isCollectionPartitioned: Boolean?, partitionKey: Int) =
            if (isCollectionPartitioned == true) session.sql.quoteIdent("${headCollectionId}_p${Static.PARTITION_ID[partitionKey]}") else collectionIdQuoted

    private fun groupByPartition(isCollectionPartitioned: Boolean?, allOperations: List<FeatureRow>) =
            if (isCollectionPartitioned == true) {
                allOperations.groupBy { it.partition }
            } else {
                allOperations.groupBy { -1 }
            }

    internal fun executeBatchDeleteFromDel(delCollectionIdQuoted: String, featureIdsToDeleteFromDel: MutableList<String>) {
        if (featureIdsToDeleteFromDel.isNotEmpty()) {
            session.sql.execute("DELETE FROM  $delCollectionIdQuoted WHERE id = ANY($1)", arrayOf(featureIdsToDeleteFromDel.toTypedArray()))
        }
    }

    internal fun calculateOpToPerform(row: FeatureRow, existingFeatures: IMap): Int {
        return if (row.xyzOp.op() == XYZ_OP_UPSERT) {
            if (existingFeatures.containsKey(row.id())) {
                XYZ_OP_UPDATE
            } else {
                XYZ_OP_CREATE
            }
        } else {
            row.xyzOp.op()
        }
    }

    fun addDelStmt(stmt: IPlv8Plan, row: IMap) {
        stmt.setLong(1, row[COL_TXN])
        stmt.setInt(2, row[COL_UID])
        stmt.setShort(3, row[COL_ACTION])
        stmt.setInt(4, row[COL_VERSION])
        stmt.setLong(5, row[COL_UPDATE_AT])
        stmt.setLong(6, row[COL_AUTHOR_TS])
        stmt.setString(7, row[COL_AUTHOR])
        stmt.setString(8, row[COL_APP_ID])
        stmt.setString(9, row[COL_ID])
        stmt.addBatch()
    }

    fun addCopyDelToHstStmt(stmt: IPlv8Plan, row: IMap) {
        stmt.setString(1, row[COL_ID])
        stmt.addBatch()
    }

    fun addInsertStmt(stmt: IPlv8Plan, row: IMap) {
        setAllColumnsOnStmt(stmt, row)
        stmt.addBatch()
    }

    fun addUpdateHeadStmt(stmt: IPlv8Plan, row: IMap) {
        setAllColumnsOnStmt(stmt, row)
        stmt.setString(19, row[COL_ID])
        stmt.addBatch()
    }

    fun addCopyHeadToHstStmt(stmt: IPlv8Plan, row: IMap, isHstDisabled: Boolean?) {
        if (isHstDisabled == false) {
            session.ensureHistoryPartition(headCollectionId, session.txn())
            session.ensureHistoryPartition(headCollectionId, NakshaTxn(Jb.int64.ZERO()))

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
        stmt.setString(14, row[COL_GEO_GRID])
        stmt.setString(15, row[COL_ID])
        stmt.setBytes(16, row[COL_TAGS])
        stmt.setBytes(17, row[COL_GEOMETRY])
        stmt.setBytes(18, row[COL_FEATURE])
    }

    internal fun quotedHst(collectionHeadId: String) = session.sql.quoteIdent("${collectionHeadId}_hst")

    internal fun quotedDel(collectionHeadId: String) = session.sql.quoteIdent("${collectionHeadId}_del")

    internal fun executeBatch(stmt: IPlv8Plan) {
        val result = stmt.executeBatch()
        if (result.isNotEmpty() && result[0] == -3) {
            // java.sql.Statement.EXECUTE_FAILED
            throw NakshaException.forBulk(ERR_FATAL, "error in bulk statement")
        }
    }

    internal fun existingFeatures(ids: List<String>) = if (ids.isNotEmpty()) {
        session.queryForExisting(headCollectionId, ids, wait = false)
    } else {
        newMap()
    }

    class PartitionPlans(
            val partitionHeadQuoted: String,
            val delCollectionIdQuoted: String,
            val hstCollectionIdQuoted: String,
            val session: NakshaSession) {

        val insertHeadPlan: IPlv8Plan by lazy {
            session.sql.prepare("INSERT INTO $partitionHeadQuoted ($COL_ALL) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18)", COL_ALL_TYPES)
        }
        val updateHeadPlan: IPlv8Plan by lazy {
            session.sql.prepare("""
                    UPDATE $partitionHeadQuoted 
                    SET $COL_TXN_NEXT=$1, $COL_TXN=$2, $COL_UID=$3, $COL_PTXN=$4,$COL_PUID=$5,$COL_GEO_TYPE=$6,$COL_ACTION=$7,$COL_VERSION=$8,$COL_CREATED_AT=$9,$COL_UPDATE_AT=$10,$COL_AUTHOR_TS=$11,$COL_AUTHOR=$12,$COL_APP_ID=$13,$COL_GEO_GRID=$14,$COL_ID=$15,$COL_TAGS=$16,$COL_GEOMETRY=$17,$COL_FEATURE=$18 WHERE id=$19
                    """.trimIndent(),
                    arrayOf(*COL_ALL_TYPES, SQL_STRING))
        }
        val insertDelPlan: IPlv8Plan by lazy {
            // ptxn + puid = txn + uid (as we generate new state in _del)
            session.sql.prepare("""
                    INSERT INTO $delCollectionIdQuoted ($COL_ALL) 
                    SELECT 0,$1,$2,$COL_TXN,$COL_UID,$COL_GEO_TYPE,$3,$4,$COL_CREATED_AT,$4,$5,$6,$7,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE 
                        FROM $partitionHeadQuoted WHERE id = $8""".trimIndent(),
                    arrayOf(SQL_INT64, SQL_INT32, SQL_INT16, SQL_INT32, SQL_INT64, SQL_INT64, SQL_STRING, SQL_STRING, SQL_STRING))
        }

        val copyHeadToHstPlan: IPlv8Plan by lazy {
            session.sql.prepare("""
                INSERT INTO $hstCollectionIdQuoted ($COL_ALL) 
                SELECT $1,$COL_TXN,$COL_UID,$COL_PTXN,$COL_PUID,$COL_GEO_TYPE,$COL_ACTION,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR_TS,$COL_AUTHOR,$COL_APP_ID,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE 
                    FROM $partitionHeadQuoted WHERE id = $2
                """.trimIndent(), arrayOf(SQL_INT64, SQL_STRING))
        }

        val copyDelToHstPlan: IPlv8Plan by lazy {
            session.sql.prepare("INSERT INTO $hstCollectionIdQuoted ($COL_ALL) SELECT $COL_ALL FROM $delCollectionIdQuoted WHERE id = $1", arrayOf(SQL_STRING))
        }
    }
}