package com.here.naksha.lib.plv8

import NakshaBulkLoaderPlan
import com.here.naksha.lib.jbon.IMap
import com.here.naksha.lib.jbon.XYZ_OP_CREATE
import com.here.naksha.lib.jbon.XYZ_OP_DELETE
import com.here.naksha.lib.jbon.XYZ_OP_PURGE
import com.here.naksha.lib.jbon.XYZ_OP_UPDATE
import com.here.naksha.lib.jbon.XYZ_OP_UPSERT
import com.here.naksha.lib.jbon.asArray
import com.here.naksha.lib.jbon.asMap
import com.here.naksha.lib.jbon.containsKey
import com.here.naksha.lib.jbon.get
import com.here.naksha.lib.jbon.getAny
import com.here.naksha.lib.jbon.minus
import com.here.naksha.lib.plv8.NakshaRequestOp.Companion.mapToOperations
import com.here.naksha.lib.plv8.Static.DEBUG
import com.here.naksha.lib.plv8.Static.currentMillis

class NakshaFeaturesWriter(
        val collectionId: String,
        val session: NakshaSession
) {

    private val headCollectionId = session.getBaseCollectionId(collectionId)
    private val collectionIdQuoted = session.sql.quoteIdent(collectionId)
    private val delCollectionId = "${headCollectionId}\$del"
    private val delCollectionIdQuoted = session.sql.quoteIdent(delCollectionId)
    private val hstCollectionIdQuoted = quotedHst(headCollectionId)

    private val collectionConfig = session.getCollectionConfig(headCollectionId)
    private val isCollectionPartitioned: Boolean? = collectionConfig[NKC_PARTITION]
    private val isHistoryDisabled: Boolean? = collectionConfig[NKC_DISABLE_HISTORY]

    fun writeFeatures(
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?>,
            geo_type_arr: Array<Short>,
            geo_arr: Array<ByteArray?>,
            tags_arr: Array<ByteArray?>,
            minResult: Boolean = false
    ): ITable {
        val START = currentMillis()
        val START_MAPPING = currentMillis()
        val operations = mapToOperations(headCollectionId, op_arr, feature_arr, geo_type_arr, geo_arr, tags_arr)
        val END_MAPPING = currentMillis()

        session.sql.execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, minResult)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, minResult)
        val END_LOADING = currentMillis()

        val START_PREPARE = currentMillis()
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, minResult)

        for (op in operations.operations) {
            val opType = calculateOpToPerform(op, existingFeatures, collectionConfig)
            when (opType) {
                XYZ_OP_CREATE ->
                    plan.addCreate(op)

                XYZ_OP_UPDATE ->
                    plan.addUpdate(op, existingFeatures, isHistoryDisabled)


                XYZ_OP_DELETE ->
                    plan.addDelete(op, existingFeatures, isHistoryDisabled)

                XYZ_OP_PURGE ->
                    plan.addPurge(op, existingFeatures, existingInDelFeatures, isHistoryDisabled)

                else -> throw RuntimeException("Operation $opType not supported")
            }
        }
        val END_PREPARE = currentMillis()

        val START_EXECUTION = currentMillis()
        executeAll(plan)
        val END_EXECUTION = currentMillis()

        val END = currentMillis()
        if (DEBUG) {
            println("[${op_arr.size} feature]: ${END!! - START!!}ms, loading: ${END_LOADING!! - START}ms, execution: ${END_EXECUTION!! - START_EXECUTION!!}ms, mapping: ${END_MAPPING!! - START_MAPPING!!}ms, preparing: ${END_PREPARE!! - START_PREPARE!!}ms")
        }
        return plan.result
    }

    fun writeCollections(
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?>,
            geo_type_arr: Array<Short>,
            geo_arr: Array<ByteArray?>,
            tags_arr: Array<ByteArray?>,
            minResult: Boolean = false
    ): ITable {
        val START = currentMillis()
        val START_MAPPING = currentMillis()
        val operations = mapToOperations(headCollectionId, op_arr, feature_arr, geo_type_arr, geo_arr, tags_arr)
        val END_MAPPING = currentMillis()

        session.sql.execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, minResult)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, minResult)
        val END_LOADING = currentMillis()

        val START_PREPARE = currentMillis()
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, minResult)
        val newCollection = NakshaCollection(session.globalDictManager)

        for (op in operations.operations) {
            val featureRowMap = op.rowMap
            newCollection.mapBytes(featureRowMap.getFeature())
            val lockId = Static.lockId(op.id)
            val query = "SELECT pg_try_advisory_xact_lock($1), oid FROM pg_namespace WHERE nspname = $2"
            val schemaOid = asMap(asArray(session.sql.execute(query, arrayOf(lockId, session.schema)))[0]).getAny("oid") as Int
            session.verifyCache(schemaOid)

            val opType = calculateOpToPerform(op, existingFeatures, collectionConfig)
            when (opType) {
                XYZ_OP_CREATE -> {
                    Static.collectionCreate(session.sql, newCollection.storageClass(), session.schema, schemaOid, op.id, newCollection.geoIndex(), newCollection.partition())
                    plan.addCreate(op)
                }

                XYZ_OP_UPDATE -> {
                    plan.addUpdate(op, existingFeatures, isHistoryDisabled)
                }

                XYZ_OP_DELETE -> {
                    Static.collectionDrop(session.sql, op.id)
                    plan.addDelete(op, existingFeatures, isHistoryDisabled)
                }

                XYZ_OP_PURGE -> {
                    Static.collectionDrop(session.sql, op.id)
                    plan.addPurge(op, existingFeatures, existingInDelFeatures, isHistoryDisabled)
                }

                else -> throw RuntimeException("Operation $opType not supported")
            }
        }
        val END_PREPARE = currentMillis()

        val START_EXECUTION = currentMillis()
        executeAll(plan)
        val END_EXECUTION = currentMillis()

        val END = currentMillis()
        if (DEBUG) {
            println("[${op_arr.size} feature]: ${END!! - START!!}ms, loading: ${END_LOADING!! - START}ms, execution: ${END_EXECUTION!! - START_EXECUTION!!}ms, mapping: ${END_MAPPING!! - START_MAPPING!!}ms, preparing: ${END_PREPARE!! - START_PREPARE!!}ms")
        }
        return plan.result
    }

    private fun executeAll(plan: NakshaBulkLoaderPlan) {
        // 1.
        plan.executeBatchDeleteFromDel(delCollectionIdQuoted, plan.featureIdsToDeleteFromDel)
        // 3.
        plan.executeBatch(plan.insertDelPlan)
        // 4. insert to history and update head
        plan.executeBatch(plan.copyHeadToHstPlan)
        plan.executeBatch(plan.updateHeadPlan)
        plan.executeBatch(plan.deleteHeadPlan)
        // 5. copy del to hst
        plan.executeBatch(plan.copyDelToHstPlan)
        // 6.
        plan.executeBatch(plan.insertHeadPlan)
        // 7. purge
        plan.executeBatchDeleteFromDel(delCollectionIdQuoted, plan.featuresToPurgeFromDel)
    }

    private fun nakshaBulkLoaderPlan(partition: Int?, minResult: Boolean) = if (isCollectionPartitioned == true && partition != null) {
        if (DEBUG) println("Insert into a single partition #$partition (isCollectionPartitioned: ${isCollectionPartitioned})")
        NakshaBulkLoaderPlan(partition, collectionId, getPartitionHeadQuoted(true, partition), delCollectionIdQuoted, hstCollectionIdQuoted, session, minResult)
    } else {
        if (DEBUG) println("Insert into a multiple partitions, therefore via HEAD (isCollectionPartitioned: ${isCollectionPartitioned})")
        NakshaBulkLoaderPlan(null, collectionId, getPartitionHeadQuoted(false, -1), delCollectionIdQuoted, hstCollectionIdQuoted, session, minResult)
    }

    private fun getPartitionHeadQuoted(isCollectionPartitioned: Boolean?, partitionKey: Int) =
            if (isCollectionPartitioned == true) session.sql.quoteIdent("${headCollectionId}\$p${Static.PARTITION_ID[partitionKey]}") else collectionIdQuoted

    internal fun calculateOpToPerform(row: NakshaRequestOp, existingFeatures: IMap, collectionConfig: IMap): Int {
        return if (row.xyzOp.op() == XYZ_OP_UPSERT) {
            if (existingFeatures.containsKey(row.id)) {
                XYZ_OP_UPDATE
            } else {
                XYZ_OP_CREATE
            }
        } else if (row.xyzOp.op() == XYZ_OP_DELETE && collectionConfig.isNkcAutoPurge()) {
            XYZ_OP_PURGE
        } else {
            row.xyzOp.op()
        }
    }

    internal fun quotedHst(collectionHeadId: String) = session.sql.quoteIdent("${collectionHeadId}\$hst")

}