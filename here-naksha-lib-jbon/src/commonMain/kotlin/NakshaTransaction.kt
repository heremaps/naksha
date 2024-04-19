@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Represents feature held in naksha~transaction.feature.
 */
@JsExport
class NakshaTransaction(dictManager: IDictManager) : JbFeature(dictManager) {
    var modifiedFeatureCount: Int = 0
    var collectionCounters: IMap = newMap()

    fun addModifiedCount(count: Int) {
        modifiedFeatureCount += count
    }

    fun addCollectionCounts(collection: String, count: Int) {
        if (!collectionCounters.containsKey(collection)) {
            collectionCounters.put(collection, count)
        } else {
            val oldCount: Int = collectionCounters[collection]!!
            collectionCounters.put(collection, oldCount + count)
        }
    }

    fun toBytes(): ByteArray {
        // FIXME maybe we should keep all in header?
        val view = JbSession.get().newDataView(ByteArray(256))
        val map = newMap()
        map["modifiedFeatureCount"] = modifiedFeatureCount
        map["collectionCounters"] = collectionCounters
        return JbBuilder(view).buildFeatureFromMap(map)
    }
}