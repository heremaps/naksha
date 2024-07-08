package naksha.psql

import naksha.base.PlatformObject
import naksha.jbon.*
import naksha.psql.PgType.Companion.BYTE_ARRAY
import naksha.psql.PgType.Companion.INT
import naksha.psql.PgType.Companion.INT64
import naksha.psql.PgType.Companion.SHORT
import naksha.psql.PgType.Companion.STRING
import kotlin.reflect.KClass

internal const val NKC_TABLE = "naksha~collections"
internal const val NKC_TABLE_ESC = "\"naksha~collections\""
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
internal const val COL_ACTION = "action"
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
    COL_ACTION,
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
    INT64.str,
    INT64.str,
    INT.str,
    INT64.str,
    INT.str,
    INT.str,
    SHORT.str,
    SHORT.str,
    INT64.str,
    INT64.str,
    INT64.str,
    STRING.str,
    STRING.str,
    INT.str,
    STRING.str,
    BYTE_ARRAY.str,
    BYTE_ARRAY.str,
    BYTE_ARRAY.str,
    BYTE_ARRAY.str,
    STRING.str,
    INT.str)
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
