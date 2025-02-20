package naksha.psql.executors.write

import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgColumn
import naksha.psql.PgPlan
import naksha.psql.PgSession
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues

class BulkWriteExecutor(
    val session: PgSession,
) : WriteExecutor {

    private val deleteFromDel: MutableMap<PgCollection, MutableSet<String>> = mutableMapOf()
    private val deleteFromHead: MutableMap<PgCollection, MutableSet<String>> = mutableMapOf()
    private val insertToHead: MutableMap<PgCollection, PgPlan> = mutableMapOf()
    private val updateHead: MutableMap<PgCollection, PgPlan> = mutableMapOf()
    private val copyHeadToHst: MutableMap<PgCollection, PgPlan> = mutableMapOf()
    private val copyHeadToDel: MutableMap<PgCollection, PgPlan> = mutableMapOf()

    override fun removeFeatureFromDel(collection: PgCollection, featureId: String) {
        collection.deleted ?: return
        var idsToDel = deleteFromDel[collection]
        if (idsToDel == null) {
            idsToDel = mutableSetOf()
            deleteFromDel[collection] = idsToDel
        }
        idsToDel.add(featureId)
    }

    override fun removeFeatureFromHead(collection: PgCollection, featureId: String) {
        var idsToDel = deleteFromHead[collection]
        if (idsToDel == null) {
            idsToDel = mutableSetOf()
            deleteFromHead[collection] = idsToDel
        }
        idsToDel.add(featureId)
    }

    override fun executeInsert(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature
    ) {
        var plan = insertToHead[collection]
        if (plan == null) {
            val quotedCollectionId = quoteIdent(collection.id)
            // TODO: Verify if allColumns is correct !!!
            plan = session.useConnection().prepare(
                """INSERT INTO $quotedCollectionId(${PgColumn.allColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
                PgColumn.allColumns.map { it.type.text }.toTypedArray()
            )
            insertToHead[collection] = plan
        }
        insertToHead[collection]!!.addBatch(allColumnValues(tuple = tuple, feature = feature, txn = session.useTransaction().txn))
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
        collection.history ?: return

        var plan = copyHeadToHst[collection]
        if (plan == null) {
            plan = createCopyPlan(collection.head.quotedName, collection.history!!.quotedName)
            copyHeadToHst[collection] = plan
        }
        plan.addBatch(
            args = arrayOf(
                session.useTransaction().txn,
                tupleNumber?.version?.txn,
                tupleNumber?.uid,
                flags,
                featureId
            )
        )
    }

    override fun copyHeadToDel(collection: PgCollection, tupleNumber: TupleNumber?, flags: Flags?, featureId: String) {
        collection.deleted ?: return

        var plan = copyHeadToDel[collection]
        if (plan == null) {
            plan = createCopyPlan(collection.head.quotedName, collection.deleted!!.quotedName)
            copyHeadToDel[collection] = plan
        }
        plan.addBatch(
            args = arrayOf(
                session.useTransaction().txn,
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
        var plan = updateHead[collection]
        if (plan == null) {
            // TODO: Verify if allColumns is correct !!!
            val columnEqualsVariable = PgColumn.allColumns.mapIndexed { index, pgColumn ->
                "${pgColumn.name}=\$${index + 1}"
            }.joinToString(separator = ",")
            val quotedHeadTable = collection.head.quotedName

            val conn = session.useConnection()
            // TODO: Verify if allColumns is correct !!!
            plan = conn.prepare(
                sql = """ UPDATE $quotedHeadTable
                   SET $columnEqualsVariable
                   WHERE ${PgColumn.id.ident}=$${PgColumn.allColumns.size + 1}
                   """.trimIndent(),
                PgColumn.allColumns.map { it.type.text }.toTypedArray()
            )
            updateHead[collection] = plan
        }
        plan.addBatch(
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
        deleteFromDel.forEach { (collection, idsToDelete) ->
            executeDelete(collection.deleted!!.quotedName, idsToDelete)
        }
        copyHeadToDel.forEach { (_, copyPlan) ->
            copyPlan.use { stmt -> stmt.executeBatch() }
        }
        copyHeadToHst.forEach { (_, copyPlan) ->
            copyPlan.use { stmt -> stmt.executeBatch() }
        }
        updateHead.forEach { (_, updatePlan) ->
            updatePlan.use { stmt -> stmt.executeBatch() }
        }
        deleteFromHead.forEach { (collection, idsToDelete) ->
            executeDelete(collection.head.quotedName, idsToDelete)
        }
        insertToHead.forEach { (_, insertPlan) ->
            insertPlan.use { stmt -> stmt.executeBatch() }
        }
    }

    private fun createCopyPlan(headTableName: String, dstTableName: String): PgPlan {

        val columnsToOverride = mutableListOf(PgColumn.txn_next, PgColumn.tn, PgColumn.flags)
        // TODO: Verify if allColumns is correct !!!
        val columnsToCopy = PgColumn.allColumns.minus(columnsToOverride.toSet())
        val columns = mutableListOf<PgColumn>()
        columns.addAll(columnsToOverride)
        columns.addAll(columnsToCopy)

        val columnNames = columns.joinToString(separator = ",")
        val copyColumnNames = columnsToCopy.joinToString(separator = ",")

        return session.useConnection().prepare(
            sql = """
                INSERT INTO $dstTableName($columnNames)
                SELECT $1,
                COALESCE($2, ${PgColumn.tn}),
                COALESCE($3, ${PgColumn.tn}),
                COALESCE($4, ${PgColumn.flags}),
                $copyColumnNames FROM $headTableName
                WHERE ${PgColumn.id.ident} = $5
            """.trimIndent(),
            columns.map { it.type.text }.toTypedArray()
        )
    }

    private fun executeDelete(quotedTable: String, idsToDelete: Set<String>) {
        val SQL = "DELETE FROM $quotedTable WHERE ${PgColumn.id.ident} = ANY($1)"
        session.useConnection().execute(SQL, arrayOf(idsToDelete.toTypedArray())).close()
    }
}