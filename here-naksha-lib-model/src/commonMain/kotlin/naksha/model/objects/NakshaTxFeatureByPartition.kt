@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Int_TYPE
import naksha.base.MapProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A map where the key is the partition identifier as string, e.g. `"15"` and the key is amount of features being part of the transaction, and being located in this partition.
 * @since 3.0
 */
@JsExport
class NakshaTxFeatureByPartition : MapProxy<String, Int>(String_TYPE, Int_TYPE) {
    companion object NakshaTxFeatureByPartition_C {
        /**
         * The [PlatformType] of [NakshaTxFeatureByPartition].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaTxFeatureByPartition::class).withPackageName(PACKAGE_NAME)
    }

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
