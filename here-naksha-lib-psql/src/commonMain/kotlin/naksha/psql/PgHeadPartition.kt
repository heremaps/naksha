@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A feature partition for performance optimisation.
 * @property head the head table.
 * @param index the index in [PgHead.partitions].
 * @since 3.0
 * @see [PgHead]
 */
@JsExport
class PgHeadPartition internal constructor(val head: PgHead, index: Int) : PgTable(
    head.collection, "${head.name}${PG_PART}${PgUtil.partitionSuffix(index)}", head.storageClass, true,
    partitionOfTable = head, partitionOfValue = index
) {
    companion object PgHeadPartition_C {
        /**
         * The [PlatformType] of [PgHeadPartition].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgHeadPartition::class).withPackageName(PACKAGE_NAME)
    }
}