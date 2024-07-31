//package naksha.psql.executors
//
//internal class NakshaBulkLoaderPlan(
//    val collectionId: String,
//    val partitionHeadQuoted: String,
//    val session: PgSession,
//    val isHistoryDisabled: Boolean?,
//    val autoPurge: Boolean,
//    val minResult: Boolean
//) {
//
//    private val headCollectionId = session.getBaseCollectionId(collectionId)
//    private val delCollectionId = "${headCollectionId}\$del"
//    private val delCollectionIdQuoted = quoteIdent(delCollectionId)
//    private val hstCollectionIdQuoted = quoteIdent("${headCollectionId}\$hst")
//
//    internal val featureIdsToDeleteFromDel = mutableListOf<String>()
//    internal val featuresToPurgeFromDel = mutableListOf<String>()
//    internal val result = mutableListOf<ResultRow>()
//
//    internal val insertToHeadBulkParams = mutableListOf<Array<Any?>>()
//    internal val copyHeadToDelBulkParams = mutableListOf<Array<Any?>>()
//    internal val insertDelToHstBulkParams = mutableListOf<Array<Any?>>()
//    internal val updateHeadBulkParams = mutableListOf<Array<Any?>>()
//    internal val deleteHeadBulkParams = mutableListOf<Array<Any?>>()
//    internal val copyHeadToHstBulkParams = mutableListOf<Array<Any?>>()
//
//    /**
//     * Returns [PgPlan] for inserts to head table.
//     * Every execution of [insertHeadPlan] creates new plan.
//     */
//    private fun insertHeadPlan(): PgPlan {
//        return session.usePgConnection().prepare(
//            """INSERT INTO $partitionHeadQuoted (
//                $COL_CREATED_AT,$COL_UPDATE_AT,$COL_TXN,$COL_UID,$COL_GEO_GRID,$COL_FLAGS,
//                $COL_APP_ID,$COL_AUTHOR,$COL_TYPE,$COL_ID,
//                $COL_FEATURE,$COL_TAGS,$COL_GEOMETRY,$COL_GEO_REF)
//                VALUES($1,$2,$3,$4,$5,
//                $6,$7,$8,$9,
//                $10,$11,$12,$13,$14)""".trimIndent(),
//            arrayOf(
//                SQL_INT64, SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT32, SQL_INT32,
//                SQL_STRING, SQL_STRING, SQL_STRING, SQL_STRING,
//                SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY
//            )
//        )
//    }
//
//    /**
//     * Returns [PgPlan] for updates of head table.
//     * Every execution of [updateHeadPlan] creates new plan.
//     */
//    private fun updateHeadPlan(): PgPlan {
//        return session.usePgConnection().prepare(
//            """
//                UPDATE $partitionHeadQuoted
//                SET $COL_TXN_NEXT=$1, $COL_TXN=$2, $COL_UID=$3, $COL_PTXN=$4,$COL_PUID=$5,$COL_FLAGS=$6,$COL_VERSION=$7,$COL_CREATED_AT=$8,$COL_UPDATE_AT=$9,$COL_AUTHOR_TS=$10,$COL_AUTHOR=$11,$COL_APP_ID=$12,$COL_GEO_GRID=$13,$COL_ID=$14,$COL_TAGS=$15,$COL_GEOMETRY=$16,$COL_FEATURE=$17,$COL_GEO_REF=$18,$COL_TYPE=$19 WHERE $COL_ID=$20
//                """.trimIndent(),
//            arrayOf(*COL_ALL_TYPES, SQL_STRING)
//        )
//    }
//
//    /**
//     * Returns [PgPlan] for deletes of head table.
//     * Every execution of [deleteHeadPlan] creates new plan.
//     */
//    private fun deleteHeadPlan(): PgPlan {
//        return session.usePgConnection().prepare(
//            """
//                DELETE FROM $partitionHeadQuoted
//                WHERE $COL_ID = $1
//                """.trimIndent(),
//            arrayOf(SQL_STRING)
//        )
//    }
//
//    /**
//     * Returns [PgPlan] for inserts to del table.
//     * Every execution of [insertDelPlan] creates new plan.
//     */
//    private fun insertDelPlan(): PgPlan {
//        // ptxn + puid = txn + uid (as we generate new state in _del)
//        return session.usePgConnection().prepare(
//            """
//                INSERT INTO $delCollectionIdQuoted ($COL_ALL)
//                SELECT $1,$2,$3,$COL_TXN,$COL_UID,$COL_FLAGS,$4,$5,$6,$7,$8,$9,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE,$COL_FNVA1
//                    FROM $partitionHeadQuoted WHERE $COL_ID = $10""".trimIndent(),
//            arrayOf(
//                SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT32,
//                SQL_INT64, SQL_INT64, SQL_INT64, SQL_STRING, SQL_STRING, SQL_STRING
//            )
//        )
//    }
//
//    /**
//     * Returns [PgPlan] to copy from del to hst table.
//     * Every execution of [insertDelToHstPlan] creates new plan.
//     */
//    private fun insertDelToHstPlan(): PgPlan {
//        return session.usePgConnection().prepare(
//            """
//                INSERT INTO $hstCollectionIdQuoted ($COL_ALL)
//                SELECT $1,$2,$3,$COL_TXN,$COL_UID,$COL_FLAGS,$4,$5,$6,$7,$8,$9,$10,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE,$COL_FNVA1
//                    FROM $partitionHeadQuoted WHERE $COL_ID = $11""".trimIndent(),
//            arrayOf(
//                SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT16, SQL_INT32, SQL_INT64,
//                SQL_INT64, SQL_INT64, SQL_STRING, SQL_STRING, SQL_STRING
//            )
//        )
//    }
//
//    /**
//     * Returns [PgPlan] to copy from head to hst table.
//     * Every execution of [copyHeadToHstPlan] creates new plan.
//     */
//    private fun copyHeadToHstPlan(): PgPlan {
//        return session.usePgConnection().prepare(
//            """
//            INSERT INTO $hstCollectionIdQuoted ($COL_ALL)
//            SELECT $1,$COL_TXN,$COL_UID,$COL_PTXN,$COL_PUID,$COL_FLAGS,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR_TS,$COL_AUTHOR,$COL_APP_ID,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE,$COL_FNVA1
//                FROM $partitionHeadQuoted WHERE $COL_ID = $2
//            """.trimIndent(), arrayOf(SQL_INT64, SQL_STRING)
//        )
//    }
//
//    fun addCreate(op: NakshaRequestOp) {
//        val dbRow = op.dbRow!!
//        addToRemoveFromDel(op.id)
//        session.rowUpdater.xyzInsert(op.collectionId, dbRow)
//        addInsertParams(dbRow)
//        if (!minResult) {
//            addResult(XYZ_EXEC_CREATED, dbRow)
//        }
//    }
//
//    fun addUpdate(op: NakshaRequestOp, existingFeature: Row?) {
//        val dbRow = op.dbRow!!
//        val headBeforeUpdate: Row = existingFeature!!
//        checkStateForAtomicOp(op.atomicUUID, headBeforeUpdate)
//
//        addToRemoveFromDel(op.id)
//        addCopyHeadToHstParams(op.id, isHistoryDisabled)
//        session.rowUpdater.xyzUpdateHead(op.collectionId, dbRow, DbRowMapper.rowToPgRow(headBeforeUpdate))
//        addUpdateHeadParams(op.dbRow)
//        if (!minResult) {
//            addResult(naksha.model.XYZ_EXEC_UPDATED, dbRow)
//        }
//    }
//
//    private fun addToRemoveFromDel(id: String) {
//        if (!autoPurge) // it's not in $del, so we can skip query
//            featureIdsToDeleteFromDel.add(id)
//    }
//
//    fun addDelete(op: NakshaRequestOp, existingFeature: Row?) {
//        addDeleteInternal(op, existingFeature)
//        if (!minResult) {
//            if (existingFeature == null) {
//                addResult(XYZ_EXEC_RETAINED)
//            } else {
//                addResult(XYZ_EXEC_DELETED, existingFeature)
//            }
//        }
//    }
//
//    fun addPurge(op: NakshaRequestOp, existingFeature: Row?, existingInDelFeature: Row?) {
//        addDeleteInternal(op, existingFeature)
//        val deletedFeatureRow: Row? = existingInDelFeature ?: existingFeature
//        checkStateForAtomicOp(op.atomicUUID, deletedFeatureRow)
//        if (!autoPurge) // it's not in $del, so we don't have to purge
//            featuresToPurgeFromDel.add(op.id)
//        if (!minResult) {
//            if (deletedFeatureRow == null) {
//                addResult(XYZ_EXEC_RETAINED)
//            } else {
//                addResult(XYZ_EXEC_PURGED, deletedFeatureRow)
//            }
//        }
//    }
//
//    internal fun executeAll() {
//        // 1.
//        executeBatchDeleteFromDel(featureIdsToDeleteFromDel)
//        // 3.
//        executeBatch(::insertDelPlan, copyHeadToDelBulkParams)
//        // 4. insert to history and update head
//        executeBatch(::copyHeadToHstPlan, copyHeadToHstBulkParams)
//        executeBatch(::updateHeadPlan, updateHeadBulkParams)
//        // 5. copy head (del state) to hst
//        executeBatch(::insertDelToHstPlan, insertDelToHstBulkParams)
//        executeBatch(::deleteHeadPlan, deleteHeadBulkParams)
//        // 6.
//        executeBatch(::insertHeadPlan, insertToHeadBulkParams)
//        // 7. purge
//        executeBatchDeleteFromDel(featuresToPurgeFromDel)
//    }
//
//    private fun addDeleteInternal(op: NakshaRequestOp, existingFeature: Row?) {
//        if (existingFeature != null) {
//            // this may throw exception (if we try to delete non-existing feature - that was deleted before)
//            val headBeforeDelete: Row = existingFeature
//            checkStateForAtomicOp(op.atomicUUID, headBeforeDelete)
//            addCopyHeadToHstParams(op.id, isHistoryDisabled)
//
//            val delRow = op.dbRow!!
//            delRow.version = headBeforeDelete.meta!!.version
//            delRow.author_ts = headBeforeDelete.meta!!.authorTs
//            delRow.updated_at = headBeforeDelete.meta!!.updatedAt
//            delRow.created_at = headBeforeDelete.meta!!.createdAt
//            session.rowUpdater.xyzDel(delRow)
//            if (!autoPurge) // do not push to del
//                addDelParams(copyHeadToDelBulkParams, delRow)
//            addDeleteHeadParams(op.id)
//            if (isHistoryDisabled == false) // even if it's autoPurge we still need a deleted copy in $hst if it's enabled.
//                addDelParams(insertDelToHstBulkParams, delRow)
//        }
//    }
//
//    private fun addInsertParams(row: PgRow) {
//        insertToHeadBulkParams.add(
//            arrayOf(
//                row.created_at, row.updated_at, row.txn, row.uid, row.geo_grid, row.flags, row.app_id, row.author,
//                row.type, row.id, row.feature, row.tags, row.geo, row.geo_ref
//            )
//        )
//    }
//
//    private fun addDelParams(params: MutableList<Array<Any?>>, pgRow: PgRow) {
//        params.add(
//            arrayOf(
//                pgRow.txn_next, pgRow.txn, pgRow.uid, pgRow.version, pgRow.created_at, pgRow.updated_at,
//                pgRow.author_ts, pgRow.author, pgRow.app_id, pgRow.id
//            )
//        )
//    }
//
//    private fun addUpdateHeadParams(row: PgRow) {
//        updateHeadBulkParams.add(
//            arrayOf(
//                row.txn_next, row.txn, row.uid, row.ptxn, row.puid, row.flags, row.version, row.created_at,
//                row.updated_at, row.author_ts, row.author, row.app_id, row.geo_grid, row.id, row.tags, row.geo,
//                row.feature, row.geo_ref, row.type, row.id
//            )
//        )
//    }
//
//    private fun addDeleteHeadParams(id: String) {
//        deleteHeadBulkParams.add(arrayOf(id))
//    }
//
//    private fun addCopyHeadToHstParams(id: String, isHstDisabled: Boolean?) {
//        if (isHstDisabled == false) {
//            copyHeadToHstBulkParams.add(arrayOf(session.txn().txn, id))
//        }
//    }
//
//    internal fun executeBatchDeleteFromDel(featureIdsToDeleteFromDel: MutableList<String>) {
//        if (featureIdsToDeleteFromDel.isNotEmpty()) {
//            session.usePgConnection().execute(
//                "DELETE FROM  $delCollectionIdQuoted WHERE id = ANY($1)",
//                arrayOf(featureIdsToDeleteFromDel.toTypedArray())
//            )
//        }
//    }
//
//    internal fun executeBatch(stmt: KFunction0<PgPlan>, bulkParams: List<Array<Any?>>) {
//        if (bulkParams.isNotEmpty()) {
//            val statement = stmt()
//            for (bulk in bulkParams) {
//                statement.addBatch(bulk)
//            }
//            val result = statement.executeBatch()
//            if (result.isNotEmpty() && result[0] == -3) {
//                // java.sql.Statement.EXECUTE_FAILED
//                throw NakshaException.forBulk(ERR_FATAL, "error in bulk statement")
//            }
//        }
//    }
//
//    /**
//     * Verifies if current request row operation has to be atomic and if it is valid against row in db.
//     *
//     * @param reqGuid - guid given in request, might be null
//     * @param currentHead - current row in DB head table
//     */
//    private fun checkStateForAtomicOp(reqGuid: String?, currentHead: Row?) {
//        if (reqGuid != null) {
//            check(currentHead != null)
//            val headUuid =
//                Guid(
//                    session.storage.id(),
//                    collectionId,
//                    currentHead.id,
//                    RowId(Version(currentHead.meta!!.version), currentHead.meta!!.uid)
//                ).toString()
//            if (reqGuid != headUuid) {
//                throw NakshaException.forId(
//                    ERR_CHECK_VIOLATION,
//                    "Atomic operation for $reqGuid is impossible, expected state: $reqGuid, actual: $headUuid",
//                    reqGuid
//                )
//            }
//        }
//    }
//
//    private fun addResult(op: String, pgRow: PgRow? = null) {
//        val row = pgRow?.let { DbRowMapper.pgRowToRow(it, session.storage, collectionId) }
//        val opEnum = JsEnum.get(op, ExecutedOp::class)
//        val resultRow = ResultRow(row = row, op = opEnum)
//        result.add(resultRow)
//    }
//
//    private fun addResult(op: String, row: Row) {
//        val resultRow = ResultRow(row = row, op = JsEnum.get(op, ExecutedOp::class))
//        result.add(resultRow)
//    }
//}