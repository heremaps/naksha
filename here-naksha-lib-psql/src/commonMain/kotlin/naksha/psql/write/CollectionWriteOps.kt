package naksha.psql.write

import naksha.model.Row
import naksha.psql.*
import naksha.psql.COL_APP_ID
import naksha.psql.COL_AUTHOR
import naksha.psql.COL_AUTHOR_TS
import naksha.psql.COL_CREATED_AT
import naksha.psql.COL_FEATURE
import naksha.psql.COL_FLAGS
import naksha.psql.COL_GEOMETRY
import naksha.psql.COL_GEO_GRID
import naksha.psql.COL_GEO_REF
import naksha.psql.COL_ID
import naksha.psql.COL_PTXN
import naksha.psql.COL_PUID
import naksha.psql.COL_TAGS
import naksha.psql.COL_TXN
import naksha.psql.COL_TXN_NEXT
import naksha.psql.COL_TYPE
import naksha.psql.COL_UID
import naksha.psql.COL_UPDATE_AT
import naksha.psql.COL_VERSION

internal data class CollectionWriteOps(
    val headCollectionId: String,
    val operations: List<NakshaRequestOp>,
    val idsToModify: List<String>,
    val idsToPurge: List<String>,
    val idsToDel: List<String>,
    /**
     * If all features are in one partition, this holds the partition id, otherwise _null_.
     */
    val partition: Int?
) {
    fun getExistingHeadFeatures(session: NakshaSession, minResult: Boolean) =
        queryForExistingFeatures(session, headCollectionId, idsToModify, emptyIfMinResult(idsToDel, minResult), false)

    fun getExistingDelFeatures(session: NakshaSession, minResult: Boolean) =
        queryForExistingFeatures(
            session,
            "${headCollectionId}\$del",
            idsToPurge,
            emptyIfMinResult(idsToPurge, minResult),
            false
        )

    fun queryForExistingFeatures(
        session: NakshaSession,
        collectionId: String,
        idsSmallFetch: List<String>,
        idsFullFetch: List<String>,
        wait: Boolean
    ): Map<String, Row> = if (idsSmallFetch.isNotEmpty()) {
        val waitOp = if (wait) "" else "NOWAIT"
        val collectionIdQuoted = PgUtil.quoteIdent(collectionId)
        val conn = session.usePgConnection()
        val basicQuery =
            "SELECT $COL_ID,$COL_TXN,$COL_UID,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR,$COL_AUTHOR_TS,$COL_GEO_GRID,$COL_FLAGS,$COL_APP_ID FROM $collectionIdQuoted WHERE id = ANY($1) FOR UPDATE $waitOp"
        val cursor = if (idsFullFetch.isEmpty()) {
            conn.execute(basicQuery, arrayOf(idsSmallFetch.toTypedArray()))
        } else {
            val complexQuery = """
                with 
                small as ($basicQuery),
                remaining as (SELECT $COL_ID, $COL_TXN_NEXT,$COL_PTXN,$COL_PUID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE FROM $collectionIdQuoted WHERE id = ANY($2))
                select * from small s left join remaining r on s.$COL_ID = r.$COL_ID 
            """.trimIndent()
            conn.execute(complexQuery, arrayOf(idsSmallFetch.toTypedArray(), idsFullFetch.toTypedArray()))
        }
        cursor.use {
            TODO("Fix me, what is the map returned?")
            //DbRowMapper.readRow(session.storage, collectionId, cursor, true)
        }
    } else {
        mutableMapOf()
    }

    private fun <T> emptyIfMinResult(list: List<T>, minResult: Boolean) = if (minResult) emptyList<T>() else list

}