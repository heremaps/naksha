package naksha.psql.executors.write

import naksha.base.Int64
import naksha.base.ListProxy
import naksha.model.Metadata
import naksha.model.TupleNumber
import naksha.model.Version
import naksha.model.request.WriteOp.WriteOp_C.DELETE
import naksha.model.request.WriteOp.WriteOp_C.UPDATE
import naksha.model.request.WriteOp.WriteOp_C.UPSERT
import naksha.psql.PgColumn
import naksha.psql.PgSession
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.PgCursorUtil.collectAndClose

/**
 * TODO FIXME This class is a local (request) cache, and it should be garbage-collected later. It might make sense to replace it with global feature cache (tuple cache) if it should keep all features' versions.
 */
class ExistingMetadataProvider(
    private val session: PgSession,
    writes: ListProxy<WriteExt>
) {
    // cache for metadata values <collection_id, <feature_id, metadata>
    private val cache: MutableMap<String, MutableMap<String, Metadata>> = mutableMapOf()

    init {
        // TODO: Review!
        val featuresPerCollection = writes
            .filterNotNull()
            .filter { it.op == UPDATE || it.op == UPSERT || it.op == DELETE }
            .filter { it.id != null }
            .groupBy { it.collectionId }
        for (entry in featuresPerCollection.entries) {
            val key = entry.key ?: continue
            val idsToFetch = entry.value.map { it.id!! }.toSet()
            val results = fetchCurrentMeta(key, idsToFetch)

            val metaMap = mutableMapOf<String, Metadata>()
            for (result in results) {
                metaMap[result.id] = result
            }
            cache[key] = metaMap
        }
    }

    fun get(collectionHeadName: String, featureId: String): Metadata? {
        return cache[collectionHeadName]?.get(featureId)
    }

    private fun fetchCurrentMeta(collectionHeadName: String, featureIds: Set<String>): List<Metadata> {
        // TODO: Fix me!
        val quotedHeadName = quoteIdent(collectionHeadName)
        val sql = """SELECT PgColumn.metaSelect
                     FROM $quotedHeadName
                     WHERE ${PgColumn.id.ident} = ANY($1)
            """.trimMargin()
        val fetchedMetadata = session.useConnection()
            .execute(sql, arrayOf(featureIds.toTypedArray()))
            .collectAndClose { metaFromRow(it) }
        return fetchedMetadata
    }

    private fun metaFromRow(row: PgCursorUtil.ReadOnlyRow): Metadata {
        TODO("Fix me!")
//        val tupleNumber: TupleNumber = TupleNumber.fromFullVariant(row[PgColumn.tn])
//        val updatedAt: Int64 = row.column(PgColumn.updated_at) as? Int64 ?: Int64(0)
//        return Metadata(
//            storeNumber = tupleNumber.storeNumber,
//            updatedAt = updatedAt,
//            createdAt = row.column(PgColumn.created_at) as? Int64 ?: updatedAt,
//            authorTs = row[PgColumn.author_ts],
//            nextVersion = maybeVersion(row.column(PgColumn.txn_next)),
//            version = tupleNumber.version,
//            prevVersion = maybeVersion(row.column(PgColumn.ptxn)),
//            uid = tupleNumber.uid,
//            puid = row.column(PgColumn.puid) as? Int,
//            hash = row[PgColumn.hash],
//            changeCount = row[PgColumn.change_count],
//            hereTile = row[PgColumn.here_tile],
//            flags = row[PgColumn.flags],
//            id = row[PgColumn.id],
//            appId = row[PgColumn.app_id],
//            author = row.column(PgColumn.author) as? String,
//            ft = row.column(PgColumn.ft) as? String,
//            originTupleNumber = row.column(PgColumn.origin) as? String
//        )
    }

    private fun maybeVersion(rawCursorValue: Any?): Version? {
        return (rawCursorValue as? Int64)?.let {
            if (it.toInt() == 0) {
                null
            } else {
                Version(it)
            }
        }
    }
}