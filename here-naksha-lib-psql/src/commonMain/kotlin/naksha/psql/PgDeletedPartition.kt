@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A feature partition for performance optimisation.
 * @param deleted the deleted table.
 * @param index the index in the deleted table partitions array.
 * @since 3.0
 * @see [PgDeleted]
 */
@JsExport
class PgDeletedPartition(val deleted: PgDeleted, index: Int) : PgTable(
    deleted.collection, "${deleted.name}${PG_PART}${PgUtil.partitionSuffix(index)}", deleted.storageClass, true,
    partitionOfTable = deleted, partitionOfValue = index
) {
    companion object PgDeletedPartition_C {
        /**
         * The [PlatformType] of [PgDeletedPartition].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgDeletedPartition::class).withPackageName(PACKAGE_NAME)
    }
}