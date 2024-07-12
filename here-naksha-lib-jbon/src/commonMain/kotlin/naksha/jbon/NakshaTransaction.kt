@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.ObjectProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Represents feature held in naksha~transaction.feature.
 */
@JsExport
@Deprecated("Please use new class from lib-model", level = DeprecationLevel.WARNING)
class NakshaTransaction(dictManager: IDictManager) : JbFeature(dictManager) {
    var modifiedFeatureCount: Int = 0
    var collectionCounters: ObjectProxy = ObjectProxy() // TODO: If we stick with this class, make the map a Map<String, Int>!
    var seqNumber: Int? = null

    fun addModifiedCount(count: Int) {
        modifiedFeatureCount += count
    }

    fun addCollectionCounts(collection: String, count: Int) {
        if (!collectionCounters.containsKey(collection)) {
            collectionCounters.put(collection, count)
        } else {
            val oldCount: Int = collectionCounters.getAs(collection, Int::class)!!
            collectionCounters.put(collection, oldCount + count)
        }
    }

    fun toBytes(): ByteArray {
        // FIXME maybe we should keep all in header?
        val map = ObjectProxy()
        map["modifiedFeatureCount"] = modifiedFeatureCount
        map["collectionCounters"] = collectionCounters
        map["seqNumber"] = seqNumber
        return JbEncoder().buildFeatureFromMap(map)
    }
}