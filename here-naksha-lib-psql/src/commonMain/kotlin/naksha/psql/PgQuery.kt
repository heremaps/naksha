@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Int64
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * An SQL query to be executed against a Naksha table.
 * @since 3.0
 */
data class PgQuery(
    /**
     * The SQL string.
     * @since 3.0
     */
    val sql: String,

    /**
     * The arguments for &#36;1 to &#36;n.
     * @since 3.0
     */
    val argValues: Array<Any?>,

    /**
     * The argument types as specified in [PgType], so like:
     * ```kotlin
     * arrayOf(PgType.INT.toString())
     * ```
     * @since 3.0
     */
    val argTypes: Array<String>,

    /**
     * The storage-number of the storage from which the results are.
     * @since 3.0
     */
    val storageNumber: Int64,

    /**
     * The map-number of the map from which the results are.
     * @since 3.0
     */
    val mapNumber: Int,

    /**
     * If all results are from the same collection, then the collection-number of the collection from which the results are. If this is `null`, then the results are from multiple collections and each returned row contains `col_num`.
     * @since 3.0
     */
    val collectionNumber: Int?
) {

    companion object PgQuery_C {
        /**
         * The [PlatformType] of [PgQuery].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgQuery::class).withPackageName(PACKAGE_NAME)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PgQuery

        if (sql != other.sql) return false
        if (storageNumber != other.storageNumber) return false
        if (mapNumber != other.mapNumber) return false
        if (collectionNumber != other.collectionNumber) return false
        if (!argValues.contentEquals(other.argValues)) return false
        if (!argTypes.contentEquals(other.argTypes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sql.hashCode()
        result = 31 * result + storageNumber.hashCode()
        result = 31 * result + mapNumber
        result = 31 * result + (collectionNumber ?: 0)
        result = 31 * result + argValues.contentHashCode()
        result = 31 * result + argTypes.contentHashCode()
        return result
    }
}