package naksha.psql.executors.write

import naksha.base.Int64
import naksha.model.*
import naksha.model.Metadata.Metadata_C.geoGrid
import naksha.model.Metadata.Metadata_C.hash
import naksha.model.objects.NakshaFeature
import naksha.model.request.Write
import naksha.psql.PgCollection
import naksha.psql.PgColumn
import naksha.psql.PgSession
import naksha.psql.PgTable
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.executors.write.PgCursorUtil.collectAndClose
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags
import naksha.psql.executors.write.WriteFeatureUtils.tuple
import kotlin.jvm.JvmStatic

open class UpdateFeature(
    val session: PgSession
) {

    open fun execute(collection: PgCollection, write: Write): Tuple {
        val feature = write.feature?.proxy(NakshaFeature::class) ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "UPDATE without feature"
        )
        require(feature.id == write.featureId) {
            "Feature id in payload (${feature.id}) and write request (${write.featureId}) are different"
        }

        val previousMetadata = fetchCurrentMeta(collection, feature.id)
        require(feature.id == previousMetadata.id) {
            "Feature id (${feature.id}) differs from previous metadata (${previousMetadata.id})"
        }
        require(previousMetadata.nextVersion == null) {
            "Previous metadata shouldn't have 'nextVersion' but it does (${previousMetadata.nextVersion})"
        }

        val tupleNumber = newFeatureTupleNumber(collection, feature.id, session)
        val flags = resolveFlags(collection, session)
        val newVersion = Version(previousMetadata.version.txn + 1)
        val tuple = tuple(
            session.storage,
            tupleNumber,
            feature,
            metadataForNewVersion(previousMetadata, newVersion, feature, flags),
            write.attachment,
            flags
        )

        removeFeatureFromDel(collection, feature.id)
        collection.history?.let { hstTable ->
            insertHeadToHst(
                hstTable = hstTable,
                headTable = collection.head,
                txnNextVersion = newVersion,
                featureId = feature.id
            )
        }
        updateFeatureInHead(collection, tuple, feature, newVersion, previousMetadata)
        return tuple
    }


    protected fun fetchCurrentMeta(collection: PgCollection, featureId: String): Metadata {
        val quotedHeadName = quoteIdent(collection.head.name)
        val sql = """SELECT ${PgColumn.metaSelect}
                     FROM $quotedHeadName
                     WHERE $quotedIdColumn=$1
            """.trimMargin()
        val fetchedMetadata = session.usePgConnection()
            .execute(sql, arrayOf(featureId))
            .collectAndClose { metaFromRow(it) }
        require(fetchedMetadata.size == 1) {
            "Expected single metadata, got ${fetchedMetadata.size} instead (query: $sql)"
        }
        return fetchedMetadata[0]
    }

    private fun metaFromRow(row: PgCursorUtil.ReadOnlyRow): Metadata {
        val tupleNumber: TupleNumber = TupleNumber.fromByteArray(row[PgColumn.tuple_number])
        val updatedAt: Int64 = row.column(PgColumn.updated_at) as? Int64 ?: Int64(0)
        return Metadata(
            storeNumber = tupleNumber.storeNumber,
            updatedAt = updatedAt,
            createdAt = row.column(PgColumn.created_at) as? Int64 ?: updatedAt,
            authorTs = row[PgColumn.author_ts],
            nextVersion = maybeVersion(row.column(PgColumn.txn_next)),
            version = tupleNumber.version,
            prevVersion = maybeVersion(row.column(PgColumn.ptxn)),
            uid = tupleNumber.uid,
            puid = row.column(PgColumn.puid) as? Int,
            hash = row[PgColumn.hash],
            changeCount = row[PgColumn.change_count],
            geoGrid = row[PgColumn.geo_grid],
            flags = row[PgColumn.flags],
            id = row[PgColumn.id],
            appId = row[PgColumn.app_id],
            author = row.column(PgColumn.author) as? String,
            type = row.column(PgColumn.type) as? String,
            origin = row.column(PgColumn.origin) as? String
        )
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

    private fun removeFeatureFromDel(collection: PgCollection, featureId: String) {
        collection.deleted?.let { delTable ->
            val quotedDelTable = quoteIdent(delTable.name)
            session.usePgConnection()
                .execute(
                    sql = "DELETE FROM $quotedDelTable WHERE $quotedIdColumn=$1",
                    args = arrayOf(featureId)
                ).close()
        }
    }

    protected fun insertHeadToHst(
        hstTable: PgTable,
        headTable: PgTable,
        txnNextVersion: Version,
        featureId: String
    ) {
        val hstTableName = quoteIdent(hstTable.name)
        val headTableName = quoteIdent(headTable.name)
        val columnsWithoutNext = PgColumn.allWritableColumns
            .filterNot { it == PgColumn.txn_next }
            .joinToString(separator = ",")
        session.usePgConnection().execute(
            sql = """
                INSERT INTO $hstTableName(${PgColumn.txn_next.name},$columnsWithoutNext)
                SELECT $1,$columnsWithoutNext FROM $headTableName
                WHERE $quotedIdColumn='$featureId'
            """.trimIndent(),
            args = arrayOf(txnNextVersion.txn)
        ).close()
    }

    private fun metadataForNewVersion(
        previousMetadata: Metadata,
        newVersion: Version,
        feature: NakshaFeature,
        flags: Flags
    ): Metadata {
        val versionTime = session.versionTime()
        return previousMetadata.copy(
            updatedAt = versionTime,
            authorTs = if (session.options.author == null) previousMetadata.authorTs else versionTime,
            version = newVersion,
            prevVersion = previousMetadata.version,
            uid = session.uid.getAndAdd(1),
            puid = previousMetadata.puid,
            hash = hash(feature, session.options.excludePaths, session.options.excludeFn),
            changeCount = previousMetadata.changeCount + 1,
            geoGrid = geoGrid(feature),
            flags = flags,
            appId = session.options.appId,
            author = session.options.author ?: previousMetadata.author,
            id = feature.id
        )
    }

    private fun updateFeatureInHead(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature,
        newVersion: Version,
        previousMetadata: Metadata
    ): Tuple {
        val conn = session.usePgConnection()
        conn.execute(
            sql = updateStatement(collection.head.name, feature.id),
            args = WriteFeatureUtils.allColumnValues(
                tuple = tuple,
                feature = feature,
                txn = newVersion.txn,
                prevTxn = previousMetadata.version.txn,
                prevUid = previousMetadata.uid,
                changeCount = previousMetadata.changeCount + 1
            )
        ).close()
        return tuple
    }

    private fun updateStatement(headTableName: String, featureId: String): String {
        val columnEqualsVariable = PgColumn.allWritableColumns.mapIndexed { index, pgColumn ->
            "${pgColumn.name}=\$${index + 1}"
        }.joinToString(separator = ",")
        val quotedHeadTable = quoteIdent(headTableName)
        return """ UPDATE $quotedHeadTable
                   SET $columnEqualsVariable
                   WHERE $quotedIdColumn='$featureId'
                   """.trimIndent()
    }

    companion object {
        @JvmStatic
        protected val quotedIdColumn: String = quoteIdent(PgColumn.id.name)
    }
}