package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.quoteIdent
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgColumn
import naksha.psql.PgPlan
import naksha.psql.PgSession
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues

class BulkWriteExecutor(
    val session: PgSession,
) : WriteExecutor {

    private var deleteFromDel: MutableMap<PgCollection, PgPlan> = mutableMapOf()
    private var insertToHead: MutableMap<PgCollection, PgPlan> = mutableMapOf()
    private var updateHead: MutableMap<PgCollection, PgPlan> = mutableMapOf()
    private var copyHeadToHst: MutableMap<PgCollection, PgPlan> = mutableMapOf()
    private var copyHeadToDel: MutableMap<PgCollection, PgPlan> = mutableMapOf()

    override fun removeFeatureFromDel(collection: PgCollection, featureId: String) {
        if (!deleteFromDel.containsKey(collection)) {
            collection.deleted?.let { delTable ->
                val quotedDelTable = quoteIdent(delTable.name)
                val quotedIdColumn = quoteIdent(PgColumn.id.name)
                val plan = session.usePgConnection()
                    .prepare("DELETE FROM $quotedDelTable WHERE $quotedIdColumn=$1", arrayOf(PgColumn.id.type.text))
                deleteFromDel[collection] = plan
            }
        }
        deleteFromDel[collection]!!.addBatch(arrayOf(featureId))
    }

    override fun executeInsert(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature
    ) {
        if (!insertToHead.containsKey(collection)) {
            val quotedCollectionId = quoteIdent(collection.id)
            val plan = session.usePgConnection().prepare(
                """INSERT INTO $quotedCollectionId(${PgColumn.allWritableColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
                PgColumn.allWritableColumns.map { it.type.text }.toTypedArray()
            )
            insertToHead[collection] = plan
        }
        insertToHead[collection]!!.addBatch(allColumnValues(tuple = tuple, feature = feature, txn = session.transaction().txn))
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
    override fun copyHeadToHst(
        collection: PgCollection,
        tupleNumber: TupleNumber?,
        flags: Flags?,
        featureId: String
    ) {
        if (!copyHeadToHst.containsKey(collection)) {
            copyHeadToHst[collection] = createCopyPlan(collection.head.quotedName, collection.history!!.quotedName)
        }
        copyHeadToHst[collection]!!.addBatch(
            args = arrayOf(
                session.transaction().txn,
                tupleNumber?.version?.txn,
                tupleNumber?.uid,
                flags,
                featureId
            )
        )
    }

    override fun copyHeadToDel(collection: PgCollection, tupleNumber: TupleNumber?, flags: Flags?, featureId: String) {
        if (!copyHeadToDel.containsKey(collection)) {
            copyHeadToDel[collection] = createCopyPlan(collection.head.quotedName, collection.deleted!!.quotedName)
        }
        copyHeadToDel[collection]!!.addBatch(
            args = arrayOf(
                session.transaction().txn,
                tupleNumber?.version?.txn,
                tupleNumber?.uid,
                flags,
                featureId
            )
        )
    }

    override fun updateFeatureInHead(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature,
        newVersion: Version,
        previousMetadata: Metadata
    ) {
        if (!updateHead.containsKey(collection)) {
            val columnEqualsVariable = PgColumn.allWritableColumns.mapIndexed { index, pgColumn ->
                "${pgColumn.name}=\$${index + 1}"
            }.joinToString(separator = ",")
            val quotedHeadTable = collection.head.quotedName

            val conn = session.usePgConnection()
            val plan = conn.prepare(
                sql = """ UPDATE $quotedHeadTable
                   SET $columnEqualsVariable
                   WHERE ${PgColumn.id.quoted()}=$${PgColumn.allWritableColumns.size + 1}
                   """.trimIndent(),
                PgColumn.allWritableColumns.map { it.type.text }.toTypedArray()
            )
            updateHead[collection] = plan
        }
        updateHead[collection]!!.addBatch(
            args = allColumnValues(
                tuple = tuple,
                feature = feature,
                txn = newVersion.txn,
                prevTxn = previousMetadata.version.txn,
                prevUid = previousMetadata.uid,
                changeCount = previousMetadata.changeCount + 1
            ).plus(feature.id)
        )
    }

    override fun finish() {
        deleteFromDel.forEach {
            it.value.use { stmt -> stmt.executeBatch() }
        }
        copyHeadToHst.forEach {
            it.value.use { stmt -> stmt.executeBatch() }
        }
        copyHeadToDel.forEach {
            it.value.use { stmt -> stmt.executeBatch() }
        }
        updateHead.forEach {
            it.value.use { stmt -> stmt.executeBatch() }
        }
        insertToHead.forEach {
            it.value.use { stmt -> stmt.executeBatch() }
        }

    }

    private fun createCopyPlan(headTableName: String, dstTableName: String): PgPlan {

        val columnsToOverride = mutableListOf(PgColumn.txn_next, PgColumn.txn, PgColumn.uid, PgColumn.flags)
        val columnsToCopy = PgColumn.allWritableColumns.minus(columnsToOverride.toSet())
        val columns = mutableListOf<PgColumn>()
        columns.addAll(columnsToOverride)
        columns.addAll(columnsToCopy)

        val columnNames = columns.joinToString(separator = ",")
        val copyColumnNames = columnsToCopy.joinToString(separator = ",")

        return session.usePgConnection().prepare(
            sql = """
                INSERT INTO $dstTableName($columnNames)
                SELECT $1,
                COALESCE($2, ${PgColumn.txn}),
                COALESCE($3, ${PgColumn.uid}),
                COALESCE($4, ${PgColumn.flags}),
                $copyColumnNames FROM $headTableName
                WHERE ${PgColumn.id.quoted()} = $5
            """.trimIndent(),
            columns.map { it.type.text }.toTypedArray()
        )
    }
}