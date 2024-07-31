package naksha.psql

import naksha.model.Naksha
import naksha.psql.PgType.Companion.BYTE_ARRAY
import naksha.psql.PgType.Companion.INT
import naksha.psql.PgType.Companion.INT64
import naksha.psql.PgType.Companion.SHORT
import naksha.psql.PgType.Companion.STRING

/**
 * `$`: The separation string used to flag internal tables.
 */
internal const val PG_S = "\$"

/**
 * ``: The identifier for the HEAD-table, no prefix.
 */
internal const val PG_HEAD = ""

/**
 * `$del`: The identifier for the DELETION-table.
 */
internal const val PG_DEL = "${PG_S}del"

/**
 * `$hst`: The identifier for the HISTORY-table.
 */
internal const val PG_HST = "${PG_S}hst"

/**
 * `$meta`: The identifier for the META-table.
 */
internal const val PG_META = "${PG_S}meta"

/**
 * `$i_`: The prefix used for indices, followed by the index identifier, e.g. `$i_id_txn_uid`
 */
internal const val PG_IDX = "${PG_S}i_"

/**
 * `$p_`: The prefix used for numerated partitions, the final value is `$p???` with `?` being `[0-9]`.
 */
internal const val PG_PART = "${PG_S}p"

/**
 * `$y_`: The prefix used for yearly partitions, the final value is `$y????` with `?` being `[0-9]`.
 */
internal const val PG_YEAR = "${PG_S}y"

/**
 * The prefix used for all internal tables.
 */
internal const val PG_INTERNAL_PREFIX = Naksha.VIRT_PREFIX

internal const val NAKSHA_ID_SEQ = "naksha_id_seq"
internal const val NAKSHA_TXN_SEQ = "naksha_txn_seq"
internal const val MAX_POSTGRES_TOAST_TUPLE_TARGET = 32736
internal const val MIN_POSTGRES_TOAST_TUPLE_TARGET = 2048

internal const val TRANSACTIONS_COL = Naksha.VIRT_TRANSACTIONS

internal const val NKC_TABLE = Naksha.VIRT_TRANSACTIONS
internal const val NKC_TABLE_ESC = "\"${Naksha.VIRT_TRANSACTIONS}\""
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
internal const val COL_UID = "uid"
internal const val COL_PTXN = "ptxn"
internal const val COL_PUID = "puid"
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
    COL_UID,
    COL_PTXN,
    COL_PUID,
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


var TEMPORARY_TABLESPACE = "temp"
