package com.here.naksha.lib.plv8

import NakshaBulkLoaderPlan
import com.here.naksha.lib.base.Base
import com.here.naksha.lib.base.NakCollection
import com.here.naksha.lib.base.NakSuccessResponse
import com.here.naksha.lib.base.NakWriteCollections
import com.here.naksha.lib.base.NakWriteFeatures
import com.here.naksha.lib.base.WriteFeature
import com.here.naksha.lib.base.WriteRow
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
        val session: NakshaSession,
        val modifyCounters: Boolean = true
) {

    private val headCollectionId = session.getBaseCollectionId(collectionId)

    private val collectionConfig = session.getCollectionConfig(headCollectionId)

    fun writeFeatures(writeRequest: NakWriteFeatures): NakSuccessResponse {
        val START = currentMillis()
        val START_MAPPING = currentMillis()
        val operations = mapToOperations(headCollectionId, writeRequest, session, collectionConfig.getPartitions())
        val END_MAPPING = currentMillis()

        session.sql.execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, writeRequest.noResults)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, writeRequest.noResults)
        val END_LOADING = currentMillis()

        val START_PREPARE = currentMillis()
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, writeRequest.noResults, collectionConfig.isDisableHistory(), collectionConfig.isAutoPurge())
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
        if (modifyCounters) {
            // no exception was thrown - execution succeeded, we can increase transaction counter
            session.transaction.addModifiedCount(writeRequest.rows.size)
            session.transaction.addCollectionCounts(collectionId, writeRequest.rows.size)
        }
        val END_EXECUTION = currentMillis()

        val END = currentMillis()
        if (DEBUG) {
            println("[${writeRequest.rows.size} feature]: ${END!! - START!!}ms, loading: ${END_LOADING!! - START}ms, execution: ${END_EXECUTION!! - START_EXECUTION!!}ms, mapping: ${END_MAPPING!! - START_MAPPING!!}ms, preparing: ${END_PREPARE!! - START_PREPARE!!}ms")
        }
        return NakSuccessResponse(rows = plan.result.toTypedArray())
    }

    fun writeCollections(writeRequest: NakWriteCollections): NakSuccessResponse {
        val operations = mapToOperations(headCollectionId, writeRequest, session, collectionConfig.getPartitions())

        session.sql.execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, writeRequest.noResults)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, writeRequest.noResults)
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, writeRequest.noResults, collectionConfig.isDisableHistory(), collectionConfig.isAutoPurge())

        for (op in operations.operations) {
            val newCollection = when (op.writeReq) {
                is WriteFeature -> Base.assign(op.writeReq.feature!!, NakCollection.klass)
                else -> throw RuntimeException("add support for WriteRow collection")
            }

            val query = "SELECT oid FROM pg_namespace WHERE nspname = $1"
            val schemaOid = asMap(asArray(session.sql.execute(query, arrayOf(session.schema)))[0]).getAny("oid") as Int
            session.verifyCache(schemaOid)

            val existingFeature: IMap? = existingFeatures[op.id]
            val opType = calculateOpToPerform(op, existingFeature, collectionConfig)
            when (opType) {
                XYZ_OP_CREATE -> {
                    Static.collectionCreate(session.sql, newCollection.getStorageClass(), session.schema, schemaOid, op.id, newCollection.getGeoIndex(), newCollection.getPartitions())
                    plan.addCreate(op)
                }

                XYZ_OP_UPDATE -> {
                    plan.addUpdate(op, existingFeature)
                }

                XYZ_OP_DELETE -> {
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
        return NakSuccessResponse(rows = plan.result.toTypedArray())
    }

    private fun nakshaBulkLoaderPlan(partition: Int?, minResult: Boolean, isHistoryDisabled: Boolean?, autoPurge: Boolean): NakshaBulkLoaderPlan {
        val isCollectionPartitioned: Boolean = collectionConfig.hasPartitions()
        return if (isCollectionPartitioned && partition != null) {
            if (DEBUG) println("Insert into a single partition #$partition (isCollectionPartitioned: ${isCollectionPartitioned})")
            NakshaBulkLoaderPlan(collectionId, getPartitionHeadQuoted(true, partition), session, isHistoryDisabled, autoPurge, minResult)
        } else {
            if (DEBUG) println("Insert into a multiple partitions, therefore via HEAD (isCollectionPartitioned: ${isCollectionPartitioned})")
            NakshaBulkLoaderPlan(collectionId, getPartitionHeadQuoted(false, -1), session, isHistoryDisabled, autoPurge, minResult)
        }
    }

    private fun getPartitionHeadQuoted(isCollectionPartitioned: Boolean?, partitionKey: Int) =
            if (isCollectionPartitioned == true) session.sql.quoteIdent("${headCollectionId}\$p${Static.PARTITION_ID[partitionKey]}") else session.sql.quoteIdent(collectionId)

    internal fun calculateOpToPerform(row: NakshaRequestOp, existingFeature: IMap?, collectionConfig: NakCollection): Int {
        return if (row.writeReq.op == XYZ_OP_UPSERT) {
            if (existingFeature != null) {
                XYZ_OP_UPDATE
            } else {
                XYZ_OP_CREATE
            }
        } else if (row.writeReq.op == XYZ_OP_DELETE && collectionConfig.isAutoPurge()) {
            XYZ_OP_PURGE
        } else {
            row.writeReq.op
        }
    }
}