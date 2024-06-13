package naksha.plv8.write

import naksha.model.response.Row
import naksha.plv8.*
import naksha.plv8.COL_ACTION
import naksha.plv8.COL_APP_ID
import naksha.plv8.COL_AUTHOR
import naksha.plv8.COL_AUTHOR_TS
import naksha.plv8.COL_CREATED_AT
import naksha.plv8.COL_FEATURE
import naksha.plv8.COL_FLAGS
import naksha.plv8.COL_GEOMETRY
import naksha.plv8.COL_GEO_GRID
import naksha.plv8.COL_GEO_REF
import naksha.plv8.COL_ID
import naksha.plv8.COL_PTXN
import naksha.plv8.COL_PUID
import naksha.plv8.COL_TAGS
import naksha.plv8.COL_TXN
import naksha.plv8.COL_TXN_NEXT
import naksha.plv8.COL_TYPE
import naksha.plv8.COL_UID
import naksha.plv8.COL_UPDATE_AT
import naksha.plv8.COL_VERSION

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
        val collectionIdQuoted = session.sql.quoteIdent(collectionId)
        val basicQuery =
            "SELECT $COL_ID,$COL_TXN,$COL_UID,$COL_ACTION,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR,$COL_AUTHOR_TS,$COL_GEO_GRID FROM $collectionIdQuoted WHERE id = ANY($1) FOR UPDATE $waitOp"
        val result = if (idsFullFetch.isEmpty()) {
            session.sql.execute(basicQuery, arrayOf(idsSmallFetch.toTypedArray()))
        } else {
            val complexQuery = """
                with 
                small as ($basicQuery),
                remaining as (SELECT $COL_ID, $COL_TXN_NEXT,$COL_PTXN,$COL_PUID,$COL_FLAGS,$COL_APP_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE FROM $collectionIdQuoted WHERE id = ANY($2))
                select * from small s left join remaining r on s.$COL_ID = r.$COL_ID 
            """.trimIndent()
            session.sql.execute(complexQuery, arrayOf(idsSmallFetch.toTypedArray(), idsFullFetch.toTypedArray()))
        }
        val rows = session.sql.rows(result)
        DbRowMapper.toMap(rows, session.storage)
    } else {
        mutableMapOf()
    }

    private fun <T> emptyIfMinResult(list: List<T>, minResult: Boolean) = if (minResult) emptyList<T>() else list

}