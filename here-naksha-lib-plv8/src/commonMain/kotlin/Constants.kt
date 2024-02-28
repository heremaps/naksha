@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.plv8

internal const val NKC_PARTITION = "partition"
internal const val NKC_POINTS_ONLY = "pointsOnly"
internal const val NKC_DISABLE_HISTORY = "disableHistory"
internal const val NKC_MAX_AGE = "maxAge"
internal const val NKC_ESTIMATED_FEATURE_COUNT = "estimatedFeatureCount"

internal const val COL_TXN = "txn"
internal const val COL_TXN_NEXT = "txn_next"
internal const val COL_UID = "uid"
internal const val COL_GEO_TYPE = "geo_type"
internal const val COL_ID = "id"
internal const val COL_XYZ = "xyz"
internal const val COL_TAGS = "tags"
internal const val COL_GEOMETRY = "geo"
internal const val COL_FEATURE = "feature"

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
