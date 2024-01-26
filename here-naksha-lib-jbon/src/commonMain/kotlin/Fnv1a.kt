@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A helper class to perform an FNV-1a hashing.
 */
@JsExport
class Fnv1a {
    /**
     * The current hash.
     */
    var hash = -0x7ee3623b // 0x811C9DC5

    /**
     * Reset the hash to the default initial value.
     * @return this.
     */
    fun reset(): Fnv1a {
        // 0x811C9DC5
        hash = -0x7ee3623b
        return this
    }

    /**
     * Hash a single byte.
     * @param v The value to hash.
     * @return this.
     */
    fun int8(v: Byte): Fnv1a {
        hash = hash xor (v.toInt() and 0xff)
        hash *= 16777619
        return this
    }

    /**
     * Hash a short that was read in big-endian encoding. This is the default encoding used by [IDataView].
     * @param v The value to hash.
     * @return this.
     */
    fun int16BE(v: Short): Fnv1a {
        hash = hash xor ((v.toInt() and 0xffff) ushr 8)
        hash *= 16777619
        hash = hash xor (v.toInt() and 0xff)
        hash *= 16777619
        return this
    }

    /**
     * Hash an short that was read in little-endian encoding.
     * @param v The value to hash.
     * @return this.
     */
    fun int16LE(v: Short): Fnv1a {
        hash = hash xor (v.toInt() and 0xff)
        hash *= 16777619
        hash = hash xor ((v.toInt() and 0xffff) ushr 8)
        hash *= 16777619
        return this
    }

    /**
     * Hash an integer that was read in big-endian encoding. This is the default encoding used by [IDataView].
     * @param v The value to hash.
     * @return this.
     */
    fun int32BE(v: Int): Fnv1a {
        hash = hash xor (v ushr 24)
        hash *= 16777619
        hash = hash xor ((v ushr 16) and 0xff)
        hash *= 16777619
        hash = hash xor ((v ushr 8) and 0xff)
        hash *= 16777619
        hash = hash xor (v and 0xff)
        hash *= 16777619
        return this
    }

    /**
     * Hash an integer that was read in little-endian encoding.
     * @param v The value to hash.
     * @return this.
     */
    fun int32LE(v: Int): Fnv1a {
        hash = hash xor (v and 0xff)
        hash *= 16777619
        hash = hash xor ((v ushr 8) and 0xff)
        hash *= 16777619
        hash = hash xor ((v ushr 16) and 0xff)
        hash *= 16777619
        hash = hash xor (v ushr 24)
        hash *= 16777619
        return this
    }
}