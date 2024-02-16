@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.math.round

/**
 * Abstraction to environment APIs, from JVM, Browser, PLV8, ...
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
interface IEnv {
    /**
     * Clip the end, so that the end is already greater/equal [offset].
     * @param bytes The byte-array to clip to.
     * @param offset The first byte that should be used.
     * @param size The size.
     * @return The end-offset (the first byte not to use), greater or equal to offset and not larger than [ByteArray.size].
     */
    fun endOf(bytes: ByteArray, offset: Int, size: Int): Int {
        if (offset < 0) throw IllegalArgumentException("offset must not be less than zero")
        if (offset >= bytes.size) throw IllegalArgumentException("offset must be within the given byte-array")
        if (size < 0) throw IllegalArgumentException("size must not be less than zero")
        val end = offset + size
        return if (end > bytes.size) bytes.size else end
    }

    /**
     * Returns a new thread-local.
     * @return The thread-local.
     */
    fun newThreadLocal() : IThreadLocal

    /**
     * Returns the current epoch milliseconds.
     * @return The current epoch milliseconds.
     */
    fun currentMillis(): BigInt64

    /**
     * Generates a new random number between 0 and 1 (therefore with 53-bit random bits).
     * @return The new random number between 0 and 1.
     */
    fun random(): Double

    /**
     * Generates a random string that Web-URL safe and matches those of the Web-Safe Base64 encoding, so persists
     * only out of `a` to `z`, `A` to `Z`, `0` to `9`, `_` or `-`.
     * @param length The amount of characters to return, if less than or equal zero, 12 characters are used.
     * @return The random string.
     */
    fun randomString(length: Int = 12): String {
        // This way, in Javascript, we catch undefined.
        val end = if (length >= 1) length else 12
        val chars = randomCharacters
        val sb = StringBuilder()
        var i = 0
        while (i++ < end) {
            sb.append(chars[(random() * 64.0).toInt() and 63])
        }
        return sb.toString()
    }

    /**
     * Stringify the given object into a JSON string.
     * @param any The object to serialize.
     * @param pretty If the result should be pretty-printed.
     * @return The JSON text.
     */
    fun stringify(any: Any, pretty: Boolean = false): String

    /**
     * Parse the given JSON into an object.
     * @param json The JSON string.
     * @return The parsed object.
     */
    fun parse(json: String): Any

    /**
     * Tests if the given 64-bit floating point number can be converted into a 32-bit floating point number without losing information.
     * @param value The 64-bit floating point number.
     * @return _true_ if the given 64-bit float can be converted into a 32-bit one without losing information; _false_ otherwise.
     */
    fun canBeFloat32(value: Double) : Boolean

    /**
     * Tests if the given 64-bit floating point number can be converted into a 32-bit integer without losing information.
     * @param value The 64-bit floating point number.
     * @return _true_ if the given 64-bit float can be converted into a 32-bit integer without losing information; _false_ otherwise.
     */
    fun canBeInt32(value: Double) : Boolean {
        val rounded = round(value)
        return rounded == value && (rounded in MIN_INT_VALUE_AS_DOUBLE..MAX_INT_VALUE_AS_DOUBLE)
    }

    /**
     * Creates a view above the given byte-array.
     * @param bytes The byte-array for which to create a view.
     * @param offset The offset into the byte-array to map.
     * @param size The amount of byte to map, if longer than the byte-array, till the end of the byte-array.
     * @return The view to the byte-array.
     */
    fun newDataView(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size): IDataView

    /**
     * Compress bytes.
     * @param raw The bytes to compress.
     * @param offset The offset of the first byte to compress.
     * @param size The amount of bytes to compress.
     * @return The deflated (compressed) bytes.
     */
    fun lz4Deflate(raw: ByteArray, offset: Int = 0, size: Int = Int.MAX_VALUE): ByteArray

    /**
     * Decompress bytes.
     * @param compressed The bytes to decompress.
     * @param bufferSize The amount of bytes that are decompressed, if unknown, set 0.
     * @param offset The offset of the first byte to decompress.
     * @param size The amount of bytes to decompress.
     * @return The inflated (decompress) bytes.
     */
    fun lz4Inflate(compressed: ByteArray, bufferSize: Int = 0, offset: Int = 0, size: Int = Int.MAX_VALUE): ByteArray

    /**
     * Store the given global dictionary in a global cache.
     * @param dict The global dictionary to store.
     */
    fun putGlobalDictionary(dict: JbDict)

    /**
     * Removes the global dictionary from the environment cache.
     * @param dict The global dictionary to store.
     */
    fun removeGlobalDictionary(dict: JbDict)

    /**
     * Retrieve the global dictionary with the given identifier from the global cache.
     * @return The global dictionary with the given identifier; _null_ when no such dictionary exists.
     */
    fun getGlobalDictionary(id: String): JbDict?
}