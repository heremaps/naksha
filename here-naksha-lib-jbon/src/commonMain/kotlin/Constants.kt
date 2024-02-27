package com.here.naksha.lib.jbon

const val TYPE_UINT4 = 0b1000_0000
const val TYPE_SINT4 = 0b1001_0000
const val TYPE_FLOAT4 = 0b1010_0000
const val TYPE_REFERENCE = 0b1011_0000
const val TYPE_STRING = 0b1100_0000
const val TYPE_STRING_CODE_POINT = 0b0001_0000_0000 // 256
const val TYPE_TEXT_REF = 0b0001_0000_0001 // 257
const val TYPE_CONTAINER = 0b1101_0000
const val TYPE_CONTAINER_MAP = 0b0000_0000
const val TYPE_MAP = TYPE_CONTAINER or TYPE_CONTAINER_MAP
const val TYPE_CONTAINER_ARRAY = 0b0000_0100
const val TYPE_ARRAY = TYPE_CONTAINER or TYPE_CONTAINER_ARRAY
const val TYPE_CONTAINER_TEXT = 0b0000_1100
const val TYPE_TEXT = TYPE_CONTAINER or TYPE_CONTAINER_TEXT
const val TYPE_TINY_LOCAL_REF = 0b1110_0000
const val TYPE_TINY_GLOBAL_REF = 0b1111_0000

// 0b0100_0000 = Reserved
// 0b0101_0000 = Reserved
// 0b0110_0000 = Reserved
// 0b0111_0000 = Reserved
// 0b001?_???? = Reserved
// All following are matching the pattern 0b000?_???? (0-31)
const val TYPE_NULL = 0
const val TYPE_UNDEFINED = 1
const val TYPE_BOOL_TRUE = 2
const val TYPE_BOOL_FALSE = 3
const val TYPE_FLOAT32 = 4
const val TYPE_FLOAT64 = 5
const val TYPE_TIMESTAMP = 6 // UTC epoch in milliseconds
// 7 = Reserved

const val TYPE_INT8 = 8
const val TYPE_INT16 = 9
const val TYPE_INT32 = 10
const val TYPE_INT64 = 11
const val TYPE_GLOBAL_DICTIONARY = 16
const val TYPE_LOCAL_DICTIONARY = 17
const val TYPE_FEATURE = 18
const val TYPE_XYZ = 19
// 20 - 31 = Reserved
/**
 * A special type returned when the offset in a reader is invalid or for any other error.
 */
const val EOF = -1
internal const val ADD_NOTHING = 0b00
internal const val ADD_SPACE = 0b01
internal const val ADD_UNDERSCORE = 0b10
internal const val ADD_COLON = 0b11

const val UNDEFINED_STRING = "undefined"

// Internally used to encode float4
internal val TINY_FLOATS = floatArrayOf(-8f, -7f, -6f, -5f, -4f, -3f, -2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
internal val TINY_DOUBLES = doubleArrayOf(-8.0, -7.0, -6.0, -5.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)
internal const val MIN_INT_VALUE_AS_DOUBLE = Int.MIN_VALUE.toDouble()
internal const val MAX_INT_VALUE_AS_DOUBLE = Int.MAX_VALUE.toDouble()

// The type names to be used in SQL queries.
const val SQL_BOOLEAN = "boolean"
const val SQL_INT16 = "int2"
const val SQL_INT32 = "int4"
const val SQL_INT64 = "int8"
const val SQL_FLOAT32 = "real"
const val SQL_FLOAT64 = "double precision"
const val SQL_BYTE_ARRAY = "bytea"
const val SQL_STRING = "text"
const val SQL_JSON_TEXT = "json"
const val SQL_OBJECT = "jsonb"

// XYZ Variants.
const val XYZ_NS = 0
const val XYZ_OP = 1
const val XYZ_TAGS = 2

const val ACTION_CREATE = 0
const val ACTION_UPDATE = 1
const val ACTION_DELETE = 2

const val XYZ_OP_CREATE = 0
const val XYZ_OP_UPDATE = 1
const val XYZ_OP_UPSERT = 2 // aka PUT
const val XYZ_OP_DELETE = 3
const val XYZ_OP_PURGE = 4
val XYZ_OP_NAME = arrayOf("CREATE", "UPDATE", "UPSERT", "DELETE", "PURGE")
val XYZ_OP_INT = arrayOf(XYZ_OP_CREATE, XYZ_OP_UPDATE, XYZ_OP_UPSERT, XYZ_OP_DELETE, XYZ_OP_PURGE)

// Feature was read.
const val XYZ_EXEC_READ = "READ"
// Feature was created.
const val XYZ_EXEC_CREATED = "CREATED"
// Feature was updated.
const val XYZ_EXEC_UPDATED = "UPDATED"
// Feature was deleted.
const val XYZ_EXEC_DELETED = "DELETED"
// Feature was purged.
const val XYZ_EXEC_PURGED = "PURGED"
// Feature did not change, returns current state, which may be null!
const val XYZ_EXEC_RETAINED = "RETAINED"
// Operation failed.
const val XYZ_EXEC_ERROR = "ERROR"

/**
 * An array with the Web-Safe Base-64 characters.
 */
val randomCharacters = CharArray(64) {
    when (it) {
        in 0..9 -> ('0'.code + it).toChar()
        in 10..35 -> ('a'.code + (it - 10)).toChar()
        in 36..61 -> ('A'.code + (it - 36)).toChar()
        62 -> '_'
        63 -> '-'
        else -> throw IllegalStateException()
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun newDataView(size: Int) = Jb.env.newDataView(ByteArray(size))

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun asArray(any: Any?): Array<Any?> = any as Array<Any?>

fun Exception.rootCause(): Exception {
    var e = this
    while (true) {
        val cause = e.cause
        if (cause == null || cause == e || !(cause is Exception)) return e
        e = cause
    }
}