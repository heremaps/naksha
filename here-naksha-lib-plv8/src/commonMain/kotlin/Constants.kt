@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*

const val ERR_FATAL = "N0000"
const val ERR_ID_MISSING = "N1001"
const val ERR_CONFLICT = "N1002"

const val C_PARTITION = "partitionHead"
const val C_POINTS_ONLY = "pointsOnly"

inline fun partition(feature: IMap) : Boolean? = feature[C_PARTITION]
inline fun pointsOnly(feature: IMap) : Boolean? = feature[C_POINTS_ONLY]
