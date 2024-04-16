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

    private val collectionConfig = session.getCollectionConfig(headCollectionId)
    private val isCollectionPartitioned: Boolean? = collectionConfig[NKC_PARTITION]
    private val isHistoryDisabled: Boolean? = collectionConfig[NKC_DISABLE_HISTORY]

    fun writeFeatures(
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?>,
            geo_type_arr: Array<Short>,
            geo_arr: Array<ByteArray?>,
            tags_arr: Array<ByteArray?>,
            minResult: Boolean
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
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, minResult, isHistoryDisabled)

        for (op in operations.operations) {
            val existingFeature: IMap? = existingFeatures[op.id]
            val opType = calculateOpToPerform(op, existingFeature, collectionConfig)
            when (opType) {
                XYZ_OP_CREATE -> plan.addCreate(op)
                XYZ_OP_UPDATE -> plan.addUpdate(op, existingFeature)
                XYZ_OP_DELETE -> plan.addDelete(op, existingFeature)
                XYZ_OP_PURGE -> plan.addPurge(op, existingFeature, existingInDelFeatures[op.id])
                else -> throw RuntimeException("Operation $opType not supported")
            }
        }
        val END_PREPARE = currentMillis()

        val START_EXECUTION = currentMillis()
        plan.executeAll()
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
            minResult: Boolean
    ): ITable {
        val operations = mapToOperations(headCollectionId, op_arr, feature_arr, geo_type_arr, geo_arr, tags_arr)

        session.sql.execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, minResult)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, minResult)
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, minResult, isHistoryDisabled)
        val newCollection = NakshaCollection(session.globalDictManager)

        for (op in operations.operations) {
            val featureRowMap = op.rowMap
            newCollection.mapBytes(featureRowMap.getFeature())

            val lockId = Static.lockId(op.id)
            val query = "SELECT pg_try_advisory_xact_lock($1), oid FROM pg_namespace WHERE nspname = $2"
            val schemaOid = asMap(asArray(session.sql.execute(query, arrayOf(lockId, session.schema)))[0]).getAny("oid") as Int
            session.verifyCache(schemaOid)

            val existingFeature: IMap? = existingFeatures[op.id]
            val opType = calculateOpToPerform(op, existingFeature, collectionConfig)
            when (opType) {
                XYZ_OP_CREATE -> {
                    Static.collectionCreate(session.sql, newCollection.storageClass(), session.schema, schemaOid, op.id, newCollection.geoIndex(), newCollection.partition())
                    plan.addCreate(op)
                }

                XYZ_OP_UPDATE -> {
                    plan.addUpdate(op, existingFeature)
                }

                XYZ_OP_DELETE -> {
                    existingFeature
                            ?: throw NakshaException.forId(ERR_COLLECTION_NOT_EXISTS, "Collection does not exist", op.id)
                    Static.collectionDrop(session.sql, op.id)
                    plan.addDelete(op, existingFeature)
                }

                XYZ_OP_PURGE -> {
                    if (existingFeature != null) {
                        Static.collectionDrop(session.sql, op.id)
                    }
                    plan.addPurge(op, existingFeature, existingInDelFeatures[op.id])
                }

                else -> throw RuntimeException("Operation $opType not supported")
            }
        }
        plan.executeAll()
        return plan.result
    }

    private fun nakshaBulkLoaderPlan(partition: Int?, minResult: Boolean, isHistoryDisabled: Boolean?) = if (isCollectionPartitioned == true && partition != null) {
        if (DEBUG) println("Insert into a single partition #$partition (isCollectionPartitioned: ${isCollectionPartitioned})")
        NakshaBulkLoaderPlan(collectionId, getPartitionHeadQuoted(true, partition), session, isHistoryDisabled, minResult)
    } else {
        if (DEBUG) println("Insert into a multiple partitions, therefore via HEAD (isCollectionPartitioned: ${isCollectionPartitioned})")
        NakshaBulkLoaderPlan(collectionId, getPartitionHeadQuoted(false, -1), session, isHistoryDisabled, minResult)
    }

    private fun getPartitionHeadQuoted(isCollectionPartitioned: Boolean?, partitionKey: Int) =
            if (isCollectionPartitioned == true) session.sql.quoteIdent("${headCollectionId}\$p${Static.PARTITION_ID[partitionKey]}") else session.sql.quoteIdent(collectionId)

    internal fun calculateOpToPerform(row: NakshaRequestOp, existingFeature: IMap?, collectionConfig: IMap): Int {
        return if (row.xyzOp.op() == XYZ_OP_UPSERT) {
            if (existingFeature != null) {
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
}