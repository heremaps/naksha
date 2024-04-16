package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.newMap

internal data class NakshaWriteOps(
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
            existingFeatures(session, headCollectionId, idsToModify, emptyIfMinResult(idsToDel, minResult))

    fun getExistingDelFeatures(session: NakshaSession, minResult: Boolean) =
            existingFeatures(session, "${headCollectionId}\$del", idsToPurge, emptyIfMinResult(idsToPurge, minResult))

    fun existingFeatures(session: NakshaSession, collectionId: String, idsSmallFetch: List<String>, idsFullFetch: List<String>) = if (idsSmallFetch.isNotEmpty()) {
        session.queryForExisting(collectionId, idsSmallFetch = idsSmallFetch, idsFullFetch = idsFullFetch, wait = false)
    } else {
        newMap()
    }

    private fun <T> emptyIfMinResult(list: List<T>, minResult: Boolean) = if (minResult) emptyList<T>() else list
}