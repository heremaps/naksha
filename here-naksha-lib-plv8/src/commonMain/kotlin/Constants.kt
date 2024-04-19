@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*

internal const val NKC_TABLE = "naksha~collections"
internal const val NKC_TABLE_ESC = "\"naksha~collections\""
internal const val NKC_PARTITION = "partition"
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
internal const val COL_GEO_TYPE = "geo_type"
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
internal const val COL_ALL = "$COL_TXN_NEXT,$COL_TXN,$COL_UID,$COL_PTXN,$COL_PUID,$COL_GEO_TYPE,$COL_ACTION,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR_TS,$COL_AUTHOR,$COL_APP_ID,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE"
internal const val COL_RETURN = "$COL_TXN_NEXT,$COL_TXN,$COL_UID,$COL_PTXN,$COL_PUID,$COL_GEO_TYPE,$COL_ACTION,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR_TS,$COL_AUTHOR,$COL_APP_ID,$COL_GEO_GRID,$COL_ID,$COL_TAGS,$COL_GEO_REF,$COL_GEOMETRY,$COL_FEATURE,$COL_TYPE"
internal val COL_ALL_TYPES = arrayOf(SQL_INT64, SQL_INT64, SQL_INT32, SQL_INT64,SQL_INT32, SQL_INT16, SQL_INT16, SQL_INT16, SQL_INT64, SQL_INT64, SQL_INT64, SQL_STRING, SQL_STRING, SQL_INT32, SQL_STRING, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_BYTE_ARRAY, SQL_STRING)

/**
 * id, grid, geo_type, geo, tags, feature
 */
internal const val COL_WRITE = "$COL_ID,$COL_GEO_GRID,$COL_GEO_TYPE,$COL_GEOMETRY,$COL_TAGS,$COL_FEATURE"

internal const val RET_OP = "op"
internal const val RET_ID = "id"
internal const val RET_XYZ = "xyz"
internal const val RET_TAGS = "tags"
internal const val RET_FEATURE = "feature"
internal const val RET_GEO_TYPE = "geo_type"
internal const val RET_GEOMETRY = "geo"
internal const val RET_ERR_NO = "err_no"
internal const val RET_ERR_MSG = "err_msg"

internal const val GEO_TYPE_NULL : Short = 0
internal const val GEO_TYPE_WKB : Short = 1
internal const val GEO_TYPE_EWKB : Short = 2
internal const val GEO_TYPE_TWKB : Short = 3

var TEMPORARY_TABLESPACE = "temp"