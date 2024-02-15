package com.here.naksha.lib.jbon

val BigInt64Max = JvmBigInt64(Long.MAX_VALUE)
val BigInt64Min = JvmBigInt64(Long.MIN_VALUE)
val positiveBigInts = Array(256) { JvmBigInt64(it.toLong()) }
val negativeBigInts = Array(256) { JvmBigInt64((it - 256).toLong()) }
