// This will be exposed
// - in JavaScript at the namespace: naksha.psql.{name}
// - jn Java at the class naksha.psql.LibPsqlKt.{name}
package naksha.psql

import naksha.model.Naksha
import naksha.model.NakshaVersion
import naksha.psql.PgType.Companion.BYTE_ARRAY
import naksha.psql.PgType.Companion.INT
import naksha.psql.PgType.Companion.INT64
import naksha.psql.PgType.Companion.SHORT
import naksha.psql.PgType.Companion.STRING

/**
 * The minimal `naksha~admin` version this version is compatible with.
 *
 * If [PgConfig.upgrade] is `false`, and the storage is of a smaller version than this one, initialization will fail. Otherwise, the storage is acceptable, so the SQL changes are not that critical that an update must be done.
 *
 * This is different from the normal [NakshaVersion.current], because `lib-psql` only increments the admin version, when the SQL functions are modified, and require an upgrade. So, even while client code may be modified, this still may not need an upgrade of the SQL functions.
 * @since 3.0
 */
val minAdminVersion = NakshaVersion.of("3.0.0-beta.24")

/**
 * The `naksha~admin` version.
 *
 * This is different from the normal [NakshaVersion.current], because `lib-psql` only increments the admin version, when the SQL functions are modified, and require an upgrade. So, even while client code may be modified, this still may not need an upgrade of the SQL functions.
 * @since 3.0
 */
val adminVersion = NakshaVersion.of("3.0.0-beta.43")

/**
 * `$`: The separation string used to flag internal tables.
 */
internal const val PG_S = "\$"

/**
 * ``: The postfix for the HEAD-table, no prefix.
 */
internal const val PG_HEAD = ""

/**
 * `$hst`: The postfix for the HISTORY-table.
 */
internal const val PG_HST = "${PG_S}hst"

/**
 * `$meta`: The postfix for the META-table.
 */
internal const val PG_META = "${PG_S}meta"

/**
 * `$i`: The prefix used for index-names. The pattern is `{tablename}$i{
 */
internal const val PG_IDX = "${PG_S}i"

/**
 * `$c`: The prefix used for constraints, followed by the identifier of the constraint.
 */
internal const val PG_CONSTRAINT = "${PG_S}c"

/**
 * `$c_nv`: The postfix of the history-constraint above [next_version][PgColumn.NextVersionColumn] _(shifted partition)_.
 */
internal const val PG_HISTORY_CONSTRAINT = "${PG_CONSTRAINT}nv"

/**
 * `$c_fn`: The postfix of the distribution-constraint above [feature-number][PgColumn.FnColumn].
 */
internal const val PG_DIST_CONSTRAINT = "${PG_CONSTRAINT}fn"

/**
 * `$h`: The prefix used for history-partitions.
 */
internal const val PG_HISTORY_PARTITION = "${PG_S}h"

/**
 * `$p`: The prefix used for distribution-partitions.
 */
internal const val PG_DIST_PARTITION = "${PG_S}p"

/**
 * The prefix used for all internal tables.
 */
internal const val PG_INTERNAL_PREFIX = Naksha.INTERNAL_PREFIX

internal const val NAKSHA_VERSION_SEQ = "naksha_version_seq"
//internal const val NAKSHA_MAP_SEQ = "naksha_map_seq"
//internal const val NAKSHA_COL_SEQ = "naksha_col_seq"

internal const val MAX_POSTGRES_TOAST_TUPLE_TARGET = 32736
internal const val MIN_POSTGRES_TOAST_TUPLE_TARGET = 2048

internal const val TRANSACTIONS_COL = Naksha.TRANSACTIONS_COL_ID

internal const val NKC_TABLE = Naksha.TRANSACTIONS_COL_ID
internal const val NKC_TABLE_ESC = "\"${Naksha.TRANSACTIONS_COL_ID}\""
internal const val NKC_PARTITION_COUNT = "partitionCount"
internal const val NKC_ID = "id"
internal const val NKC_GEO_INDEX = "geoIndex"
internal const val NKC_DISABLE_HISTORY = "disableHistory"
internal const val NKC_MAX_AGE = "maxAge"
internal const val NKC_ESTIMATED_FEATURE_COUNT = "estimatedFeatureCount"
internal const val NKC_AUTO_PURGE = "autoPurge"
internal const val NKC_STORAGE_CLASS = "storageClass"

internal const val COL_TXN_NEXT = "txn_next"
internal const val COL_TXN = "txn"
internal const val COL_PTXN = "ptxn"
internal const val COL_FLAGS = "flags"
internal const val COL_VERSION = "version"
internal const val COL_CREATED_AT = "created_at"
internal const val COL_UPDATE_AT = "updated_at"
internal const val COL_AUTHOR_TS = "author_ts"
internal const val COL_AUTHOR = "author"
internal const val COL_APP_ID = "app_id"
internal const val COL_GEO_GRID = "geo_grid"
internal const val COL_ID = "id"
internal const val COL_TAGS = "tags"
internal const val COL_GEOMETRY = "geo"
internal const val COL_GEO_REF = "geo_ref"
internal const val COL_FEATURE = "feature"
internal const val COL_TYPE = "type"
internal const val COL_ORIGIN = "origin"
internal const val COL_FNVA1 = "fnva1"
internal val COL_ALL: String = arrayOf(
    COL_TXN_NEXT,
    COL_TXN,
    COL_PTXN,
    COL_FLAGS,
    COL_VERSION,
    COL_CREATED_AT,
    COL_UPDATE_AT,
    COL_AUTHOR_TS,
    COL_AUTHOR,
    COL_APP_ID,
    COL_GEO_GRID,
    COL_ID,
    COL_TAGS,
    COL_GEOMETRY,
    COL_FEATURE,
    COL_GEO_REF,
    COL_TYPE,
    COL_FNVA1
).joinToString(",")
internal val COL_ALL_TYPES: Array<String> = arrayOf(
    INT64.text,
    INT64.text,
    INT.text,
    INT64.text,
    INT.text,
    INT.text,
    SHORT.text,
    INT64.text,
    INT64.text,
    INT64.text,
    STRING.text,
    STRING.text,
    INT.text,
    STRING.text,
    BYTE_ARRAY.text,
    BYTE_ARRAY.text,
    BYTE_ARRAY.text,
    BYTE_ARRAY.text,
    STRING.text,
    INT.text)
private fun createJoiner(): (_: String) -> String {
    var i = 0
    return {
        i++
        "${'$'}${i}"
    }
}
internal val COL_ALL_DOLLAR = COL_ALL_TYPES.joinToString(transform = createJoiner())

/**
 * id, grid, flags, geo, tags, feature
 */
internal const val COL_WRITE = "$COL_ID,$COL_GEO_GRID,$COL_FLAGS,$COL_GEOMETRY,$COL_TAGS,$COL_FEATURE"

internal const val RET_OP = "op"
internal const val RET_ID = "id"
internal const val RET_XYZ = "xyz"
internal const val RET_TAGS = "tags"
internal const val RET_FEATURE = "feature"
internal const val RET_FLAGS = "flags"
internal const val RET_GEOMETRY = "geo"
internal const val RET_ERR_NO = "err_no"
internal const val RET_ERR_MSG = "err_msg"


/**
 * Search for the occurrence of the given byte-value.
 * @param value the value to search, but be convertable to `byte` (the method will invoke `toByte()`)
 * @param start the first byte to test.
 * @param end the first byte not to test.
 * @return either the index of the [value] or -1, if not found.
 */
internal fun ByteArray.indexOf(value: Int, start: Int = 0, end: Int = size): Int {
    val VALUE = value.toByte()
    for (i in start until end) {
        if (this[i] == VALUE) return i
    }
    return -1
}