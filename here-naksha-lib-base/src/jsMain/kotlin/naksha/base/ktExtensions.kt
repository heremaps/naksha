package naksha.base

/**
 * Ensures that the given value is a real 64-bit integer.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun Int64.asInt64(): Int64 = js("BigInt.asIntN(64,this)").unsafeCast<Int64>()

/**
 * Ensures that the given value is a real unsigned 64-bit integer.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun Int64.asUint64(): Int64 = js("BigInt.asUintN(64,this)").unsafeCast<Int64>()
