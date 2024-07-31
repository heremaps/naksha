package naksha.psql.write

import naksha.base.Platform.PlatformCompanion.currentMillis
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.objects.NakshaCollection
import naksha.model.TransactionCollectionInfoProxy
import naksha.model.request.op.WriteFeature
import naksha.model.request.Write.Companion.XYZ_OP_CREATE
import naksha.model.request.Write.Companion.XYZ_OP_DELETE
import naksha.model.request.Write.Companion.XYZ_OP_PURGE
import naksha.model.request.Write.Companion.XYZ_OP_UPDATE
import naksha.model.request.Write.Companion.XYZ_OP_UPSERT
import naksha.model.request.WriteRequest
import naksha.model.Row
import naksha.model.request.SuccessResponse
import naksha.psql.PgSession
import naksha.psql.PgResultSet
import naksha.psql.PgUtil
import naksha.psql.PgUtil.PgUtilCompanion.partitionPosix
import naksha.psql.write.NakshaRequestOp.Companion.mapToOperations

class SingleCollectionWriter(
    val collectionId: String,
    val session: PgSession,
    val modifyCounters: Boolean = true
) {

    private val headCollectionId = session.getBaseCollectionId(collectionId)

    private val collectionConfig = session.getCollectionConfig(headCollectionId)

    fun writeFeatures(writeRequest: WriteRequest): SuccessResponse {
        val START = currentMillis()
        val START_MAPPING = currentMillis()
        val operations = mapToOperations(headCollectionId, writeRequest, session, collectionConfig.partitions)
        val END_MAPPING = currentMillis()

        val counts = TransactionCollectionInfoProxy()
        counts.collectionId = collectionId

        session.usePgConnection().execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, writeRequest.returnResults)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, writeRequest.returnResults)
        val END_LOADING = currentMillis()

        val START_PREPARE = currentMillis()
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, writeRequest.returnResults, collectionConfig.disableHistory, collectionConfig.autoPurge)
        for (op in operations.operations) {
            val existingFeature: Row? = existingFeatures[op.id]
            val opType = calculateOpToPerform(op, existingFeature, collectionConfig)
            when (opType) {
                XYZ_OP_CREATE -> {
                    plan.addCreate(op)
                    counts.inserted++
                }
                XYZ_OP_UPDATE -> {
                    plan.addUpdate(op, existingFeature)
                    counts.updated++
                }
                XYZ_OP_DELETE -> {
                    plan.addDelete(op, existingFeature)
                    counts.deleted++
                }
                XYZ_OP_PURGE -> {
                    plan.addPurge(op, existingFeature, existingInDelFeatures[op.id])
                    counts.purged++
                }
                else -> throw RuntimeException("Operation $opType not supported")
            }
        }
        val END_PREPARE = currentMillis()

        val START_EXECUTION = currentMillis()
        plan.executeAll()
        if (modifyCounters) {
            // no exception was thrown - execution succeeded, we can increase transaction counter
            val transaction = session.transaction()
            transaction.incFeaturesModified(writeRequest.writes.size)
            transaction.addTxCollectionInfo(counts)
        }
        val END_EXECUTION = currentMillis()

        val END = currentMillis()
        logger.info("[{} feature]: {}ms, loading: {}ms, execution: {}ms, mapping: {}ms, preparing: {}ms",
            writeRequest.writes.size,
            END - START,
            END_LOADING - START,
            END_EXECUTION - START_EXECUTION,
            END_MAPPING!! - START_MAPPING!!,
            END_PREPARE!! - START_PREPARE!!
        )
        return SuccessResponse(PgResultSet(session.storage, writeRequest.rowOptions, plan.result))
    }

    fun writeCollections(writeRequest: WriteRequest): SuccessResponse {
        val operations = mapToOperations(headCollectionId, writeRequest, session, collectionConfig.partitions)

        session.usePgConnection().execute("SET LOCAL session_replication_role = replica; SET plan_cache_mode=force_custom_plan;")

        val existingFeatures = operations.getExistingHeadFeatures(session, writeRequest.returnResults)
        val existingInDelFeatures = operations.getExistingDelFeatures(session, writeRequest.returnResults)
        val plan: NakshaBulkLoaderPlan = nakshaBulkLoaderPlan(operations.partition, writeRequest.returnResults, collectionConfig.disableHistory, collectionConfig.autoPurge)

        for (op in operations.operations) {
            val query = "SELECT oid FROM pg_namespace WHERE nspname = $1"
            val schemaOid: Int = session.usePgConnection().execute(query, arrayOf(session.options.schema)).fetch()["oid"]

            val existingFeature: Row? = existingFeatures[op.id]
            val opType = calculateOpToPerform(op, existingFeature, collectionConfig)
            when (opType) {
                XYZ_OP_CREATE -> {
                    val newCollection = when (op.reqWrite) {
                        is WriteFeature ->op.reqWrite.feature.proxy(NakshaCollection::class)
                        else -> throw RuntimeException("add support for WriteRow collection")
                    }
// TODO: Fix me!!!
//                    PgStatic.collectionCreate(
//                        session.usePgConnection(),
//                        newCollection.storageClass,
//                        session.options.schema,
//                        schemaOid,
//                        op.id,
//                        newCollection.geoIndex,
//                        newCollection.partitions
//                    )
                    plan.addCreate(op)
                }

                XYZ_OP_UPDATE -> {
                    plan.addUpdate(op, existingFeature)
                }

                XYZ_OP_DELETE -> {
                    // TODO: Fix me!!!
                    //PgStatic.collectionDrop(session.usePgConnection(), op.id)
                    plan.addDelete(op, existingFeature)
                }

                XYZ_OP_PURGE -> {
                    if (existingFeature != null) {
                        // TODO: Fix me!!!
                        //PgStatic.collectionDrop(session.usePgConnection(), op.id)
                    }
                    plan.addPurge(op, existingFeature, existingInDelFeatures[op.id])
                }

                else -> throw RuntimeException("Operation $opType not supported")
            }
        }
        plan.executeAll()
        return SuccessResponse(PgResultSet(session.storage, writeRequest.rowOptions, plan.result))
    }

    private fun nakshaBulkLoaderPlan(partition: Int?, minResult: Boolean, isHistoryDisabled: Boolean?, autoPurge: Boolean): NakshaBulkLoaderPlan {
        val isCollectionPartitioned: Boolean = collectionConfig.hasPartitions()
        return if (isCollectionPartitioned && partition != null) {
            logger.info("Insert into a single partition #{} (isCollectionPartitioned: {})", partition, isCollectionPartitioned)
            NakshaBulkLoaderPlan(collectionId, getPartitionHeadQuoted(true, partition), session, isHistoryDisabled, autoPurge, minResult)
        } else {
            logger.info("Insert into a multiple partitions, therefore via HEAD (isCollectionPartitioned: {})", isCollectionPartitioned)
            NakshaBulkLoaderPlan(collectionId, getPartitionHeadQuoted(false, -1), session, isHistoryDisabled, autoPurge, minResult)
        }
    }

    private fun getPartitionHeadQuoted(isCollectionPartitioned: Boolean?, partitionKey: Int) =
        if (isCollectionPartitioned == true) PgUtil.quoteIdent("${headCollectionId}\$p${partitionPosix(partitionKey)}") else
            PgUtil.quoteIdent(collectionId)

    private fun calculateOpToPerform(row: NakshaRequestOp, existingFeature: Row?, collectionConfig: NakshaCollection): Int {
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
