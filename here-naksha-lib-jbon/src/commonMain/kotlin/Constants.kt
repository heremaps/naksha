package com.here.naksha.lib.jbon

const val TYPE_INT4 = 0b1000_0000
const val TYPE_FLOAT4 = 0b1001_0000
const val TYPE_SIZE32 = 0b1010_0000
const val TYPE_STRING = 0b1100_0000
const val TYPE_NULL = 0
const val TYPE_UNDEFINED = 1
const val TYPE_BOOL_TRUE = 2
const val TYPE_BOOL_FALSE = 3
const val TYPE_FLOAT32 = 4
const val TYPE_FLOAT64 = 5
const val TYPE_DICTIONARY = 6
const val TYPE_DOCUMENT = 7
const val TYPE_INT8 = 8
const val TYPE_INT16 = 9
//const val TYPE_INT24 = 10
const val TYPE_INT32 = 11
//const val TYPE_INT40 = 12
//const val TYPE_INT48 = 13
//const val TYPE_INT56 = 14
//const val TYPE_INT64 = 15
val TINY_FLOATS = floatArrayOf(-8f, -7f, -6f, -5f, -4f, -3f, -2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
val TINY_DOUBLES = doubleArrayOf(-8.0, -7.0, -6.0, -5.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)
