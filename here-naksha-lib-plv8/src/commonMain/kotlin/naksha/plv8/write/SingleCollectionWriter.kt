package naksha.plv8.write

import naksha.jbon.asArray
import naksha.base.Platform.Companion.currentMillis
import naksha.model.NakshaCollectionProxy
import naksha.model.request.FeatureOp
import naksha.model.request.Write.Companion.XYZ_OP_CREATE
import naksha.model.request.Write.Companion.XYZ_OP_DELETE
import naksha.model.request.Write.Companion.XYZ_OP_PURGE
import naksha.model.request.Write.Companion.XYZ_OP_UPDATE
import naksha.model.request.Write.Companion.XYZ_OP_UPSERT
import naksha.model.request.WriteRequest
import naksha.model.response.Row
import naksha.model.response.SuccessResponse
import naksha.plv8.NakshaSession
import naksha.plv8.Static
import naksha.plv8.write.NakshaRequestOp.Companion.mapToOperations
import naksha.plv8.Static.DEBUG

class SingleCollectionWriter(
    val collectionId: String,
    val session: NakshaSession,
    val modifyCounters: Boolean = true
) {

    private val headCollectionId = session.getBaseCollectionId(collectionId)

    private val collectionConfig = session.getCollectionConfig(headCollectionId)

    fun writeFeatures(writeRequest: WriteRequest): SuccessResponse {
        val START = currentMillis()
        val START_MAPPING = currentMillis()
        val operations = mapToOperations(headCollectionId, writeRequest, session, collectionConfig.partitions)
        val END_MAPPING = currentMillis()

        session.sql.execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, writeRequest.noResults)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, writeRequest.noResults)
        val END_LOADING = currentMillis()

        val START_PREPARE = currentMillis()
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, writeRequest.noResults, collectionConfig.disableHistory, collectionConfig.autoPurge)
        for (op in operations.operations) {
            val existingFeature: Row? = existingFeatures[op.id]
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
            session.transaction.incFeaturesModified(writeRequest.ops.size)
            session.transaction.addCollectionCounts(collectionId, writeRequest.ops.size)
        }
        val END_EXECUTION = currentMillis()

        val END = currentMillis()
        if (DEBUG) {
            println("[${writeRequest.ops.size} feature]: ${END - START}ms, loading: ${END_LOADING - START}ms, execution: ${END_EXECUTION - START_EXECUTION}ms, mapping: ${END_MAPPING!! - START_MAPPING!!}ms, preparing: ${END_PREPARE!! - START_PREPARE!!}ms")
        }
        return SuccessResponse(rows = plan.result)
    }

    fun writeCollections(writeRequest: WriteRequest): SuccessResponse {
        val operations = mapToOperations(headCollectionId, writeRequest, session, collectionConfig.partitions)

        session.sql.execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, writeRequest.noResults)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, writeRequest.noResults)
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, writeRequest.noResults, collectionConfig.disableHistory, collectionConfig.autoPurge)

        for (op in operations.operations) {
            val query = "SELECT oid FROM pg_namespace WHERE nspname = $1"
            val schemaOid = (asArray(session.sql.execute(query, arrayOf(session.schema)))[0] as Map<*, *>)["oid"] as Int

            val existingFeature: Row? = existingFeatures[op.id]
            val opType = calculateOpToPerform(op, existingFeature, collectionConfig)
            when (opType) {
                XYZ_OP_CREATE -> {
                    val newCollection = when (op.reqWrite) {
                        is FeatureOp ->op.reqWrite.feature.proxy(NakshaCollectionProxy::class)
                        else -> throw RuntimeException("add support for WriteRow collection")
                    }
                    Static.collectionCreate(
                        session.sql,
                        newCollection.storageClass,
                        session.schema,
                        schemaOid,
                        op.id,
                        newCollection.geoIndex,
                        newCollection.partitions
                    )
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
        return SuccessResponse(rows = plan.result)
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

    private fun calculateOpToPerform(row: NakshaRequestOp, existingFeature: Row?, collectionConfig: NakshaCollectionProxy): Int {
        return if (row.reqWrite.op == XYZ_OP_UPSERT) {
            if (existingFeature != null) {
                XYZ_OP_UPDATE
            } else {
                XYZ_OP_CREATE
            }
        } else if (row.reqWrite.op == XYZ_OP_DELETE && collectionConfig.autoPurge) {
            XYZ_OP_PURGE
        } else {
            row.reqWrite.op
        }
    }
}