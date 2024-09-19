package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.quoteIdent
import naksha.model.objects.NakshaFeature
import naksha.psql.*
import naksha.psql.PgUtil.PgUtilCompanion
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues

class InstantWriteExecutor(
    val session: PgSession,
) : WriteExecutor {

    override fun removeFeatureFromDel(collection: PgCollection, featureId: String) {
        collection.deleted?.let { delTable ->
            val quotedDelTable = quoteIdent(delTable.name)
            val quotedIdColumn = quoteIdent(PgColumn.id.name)
            session.usePgConnection()
                .execute(
                    sql = "DELETE FROM $quotedDelTable WHERE $quotedIdColumn=$1",
                    args = arrayOf(featureId)
                ).close()
        }
    }

    override fun executeInsert(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature
    ) {
        val transaction = session.transaction()
        val conn = session.usePgConnection()
        val quotedCollectionId = quoteIdent(collection.id)
        conn.execute(
            sql = """ INSERT INTO $quotedCollectionId(${PgColumn.allWritableColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn)
        ).close()
    }

    override fun copyHeadToDel(collection: PgCollection, tupleNumber: TupleNumber?, flags: Flags?, featureId: String) {
        copyHeadTo(collection.deleted!!, collection.head, tupleNumber, flags, featureId)
    }

    override fun copyHeadToHst(collection: PgCollection, tupleNumber: TupleNumber?, flags: Flags?, featureId: String) {
        copyHeadTo(collection.history!!, collection.head, tupleNumber, flags, featureId)
    }

    override fun updateFeatureInHead(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature,
        newVersion: Version,
        previousMetadata: Metadata
    ) {
        val conn = session.usePgConnection()
        conn.execute(
            sql = updateStatement(collection.head.name),
            args = allColumnValues(
                tuple = tuple,
                feature = feature,
                txn = newVersion.txn,
                prevTxn = previousMetadata.version.txn,
                prevUid = previousMetadata.uid,
                changeCount = previousMetadata.changeCount + 1
            ).plus(feature.id)
        ).close()
    }

    private fun updateStatement(headTableName: String): String {
        val columnEqualsVariable = PgColumn.allWritableColumns.mapIndexed { index, pgColumn ->
            "${pgColumn.name}=\$${index + 1}"
        }.joinToString(separator = ",")
        val quotedHeadTable = PgUtil.quoteIdent(headTableName)
        return """ UPDATE $quotedHeadTable
                   SET $columnEqualsVariable
                   WHERE ${PgColumn.id.quoted()}=$${PgColumn.allWritableColumns.size + 1}
                   """.trimIndent()
    }

    override fun removeFeatureFromHead(collection: PgCollection, featureId: String) {
        collection.head.let { headTable ->
            val quotedHeadTable = PgUtilCompanion.quoteIdent(headTable.name)
            session.usePgConnection()
                .execute(
                    sql = "DELETE FROM $quotedHeadTable WHERE ${PgColumn.id.quoted()}=$1",
                    args = arrayOf(featureId)
                ).close()
        }
    }

    override fun finish() {
        // nothing to do
    }

    //                          hst table
    //                     txn_next       txn          uid                     flags
    // CREATED/UPDATED     new (1)        old        unchanged from head     unchanged from head
    // DELETED             new (1)       new (1)      new                   with deleted action bits
    // (1) denotes the same value, taken from current txn / version of current PgSession
    /**
     * Persist the feature entry in HEAD table into the destination table (HST or DEL).
     * @param tupleNumber if intend to insert a tombstone DELETED state, provide this tuple number
     *                    of the tombstone state, with new uid and current session txn
     * @param flags the new flags. If intend to insert a tombstone DELETED state,
     *              provide the old flags but with action DELETED.
     */
    private fun copyHeadTo(
        destinationTable: PgTable,
        headTable: PgTable,
        tupleNumber: TupleNumber?,
        flags: Flags?,
        featureId: String
    ) {
        val dstTableName = PgUtil.quoteIdent(destinationTable.name)
        val headTableName = PgUtil.quoteIdent(headTable.name)
        val otherColumns = PgColumn.allWritableColumns
            .asSequence()
            .filterNot { it == PgColumn.txn_next }
            .filterNot { it == PgColumn.txn }
            .filterNot { it == PgColumn.uid }
            .filterNot { it == PgColumn.flags }
            .joinToString(separator = ",")
        session.usePgConnection().execute(
            sql = """
                INSERT INTO $dstTableName(
                ${PgColumn.txn_next.name},
                ${PgColumn.txn.name},
                ${PgColumn.uid.name},
                ${PgColumn.flags.name},
                $otherColumns)
                SELECT $1,
                COALESCE($2, ${PgColumn.txn}),
                COALESCE($3, ${PgColumn.uid}),
                COALESCE($4, ${PgColumn.flags}),
                $otherColumns FROM $headTableName
                WHERE ${PgColumn.id.quoted()} = $5
            """.trimIndent(),
            args = arrayOf(
                session.transaction().txn,
                tupleNumber?.version?.txn,
                tupleNumber?.uid,
                flags,
                featureId
            )
        ).close()
    }
}