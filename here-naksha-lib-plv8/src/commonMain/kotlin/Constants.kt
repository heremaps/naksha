@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*

const val ERR_FATAL = "N0000"
const val ERR_ID_MISSING = "N1001"

const val COL_PARTITION = "partitionHead"
const val COL_POINTS_ONLY = "pointsOnly"

inline fun partition(feature: IMap) : Boolean? = feature[COL_PARTITION]
inline fun pointsOnly(feature: IMap) : Boolean? = feature[COL_POINTS_ONLY]
