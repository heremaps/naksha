@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.PTypedMap
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A map where the key is the partition identifier as string, e.g. `"15"` and the key is amount of features being part of the transaction, and being located in this partition.
 * @since 3.0
 */
@JsExport
class NakshaTxFeatureByPartition : PTypedMap<String, Int>(String::class, Int::class) {
    /**
     * Add the given amount to the partition counter.
     * @param partitionIndex the partition index.
     * @param amount the amount to add.
     * @since 3.0
     */
    @JsName("addString")
    fun add(partitionIndex: String, amount: Int) {
        val existing = this[partitionIndex]
        if (existing == null) {
            this[partitionIndex] = amount
        } else {
            this[partitionIndex] = existing + amount
        }
    }

    /**
     * Add the given amount to the partition counter.
     * @param partitionIndex the partition index.
     * @param amount the amount to add.
     * @since 3.0
     */
    fun add(partitionIndex: Int, amount: Int) {
        add(partitionIndex.toString(), amount)
    }
}
