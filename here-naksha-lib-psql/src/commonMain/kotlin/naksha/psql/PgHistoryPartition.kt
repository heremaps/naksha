@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A feature partition for performance optimisation.
 * @property year the history year.
 * @param index the index in the history year partitions array.
 * @since 3.0
 * @see [PgHistoryYear]
 */
@JsExport
class PgHistoryPartition(val year: PgHistoryYear, index: Int) : PgTable(
    year.collection, "${year.name}${PG_PART}${PgUtil.partitionSuffix(index)}", year.storageClass, false,
    partitionOfTable = year, partitionOfValue = index
) {
    companion object PgHistoryPartition_C {
        /**
         * The [PlatformType] of [PgHistoryPartition].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgHistoryPartition::class).withPackageName(PACKAGE_NAME)
    }
}