@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The API to be provided by the platform to grant access to native capabilities.
 */
@Suppress("unused")
@JsExport
abstract class JbNative {
    companion object {
        /**
         * The reference to the platform instance set by the platform and read via [get].
         */
        internal var instance: JbNative? = null

        /**
         * Returns the platform instance.
         */
        fun get(): JbNative {
            return instance as JbNative
        }
    }

    /**
     * Creates a new empty native map. Returns [HashMap] in the JVM and **object** in JavaScript.
     * @return A new native map.
     */
    abstract fun newMap(): Any

    /**
     * Returns the API that grants access to native maps.
     * @return The API that grants access to native maps.
     */
    abstract fun map() : INativeMap

    /**
     * Returns the API that grants access to native SQL queries.
     * @return The API that grants access to native SQL queries.
     */
    abstract fun sql() : ISql

    /**
     * Returns the native logging API.
     * @return The native logging API.
     */
    abstract fun log() : INativeLog

    /**
     * Stringify the given object into a JSON string.
     * @param any The object to serialize.
     * @param pretty If the result should be pretty-printed.
     * @return The JSON text.
     */
    abstract fun stringify(any: Any, pretty: Boolean = false): String

    /**
     * Parse the given JSON into an object.
     * @param json The JSON string.
     * @return The parsed object.
     */
    abstract fun parse(json: String): Any

    /**
     * Converts an internal 64-bit integer into a platform specific.
     * @param value The internal 64-bit.
     * @return The platform specific 64-bit.
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    abstract fun longToBigInt(value: Long): Any

    /**
     * Converts a platform specific 64-bit integer into an internal one to be used for example with the [IDataView].
     * @param value The platform specific 64-bit integer.
     * @return The internal 64-bit integer.
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    abstract fun bigIntToLong(value: Any): Long

    /**
     * This is more for platform code, to create new byte-arrays the same way as Kotlin does it. For JAVA this means to create a
     * byte[], for JavaScript it means a Int8Array. Beware, that technically the Int8Array is already a view using a buffer beneath,
     * which actually is the real byte-array.
     * @param size The size of the byte-array.
     * @return The new byte array.
     */
    fun newByteArray(size: Int): ByteArray {
        return ByteArray(size)
    }

    /**
     * Creates a new builder.
     * @param globalDict The global dictionary to use for this builder.
     * @param size The size of the builder buffer.
     * @return a new builder.
     */
    fun newBuilder(globalDict: JbDict? = null, size:Int = 65536) : JbBuilder {
        return JbBuilder(newDataView(ByteArray(size)), globalDict)
    }

    /**
     * Creates a view above the given byte-array.
     * @param bytes The byte-array for which to create a view.
     * @param offset The offset into the byte-array to map.
     * @param size The amount of byte to map, if longer than the byte-array, till the end of the byte-array.
     * @return The view to the byte-array.
     */
    abstract fun newDataView(bytes: ByteArray, offset: Int = 0, size: Int = Int.MAX_VALUE): IDataView

    /**
     * Compress bytes.
     * @param bytes The bytes to compress.
     * @param offset The offset of the first byte to compress.
     * @param size The amount of bytes to compress.
     * @return The deflated (compressed) bytes.
     */
    abstract fun lz4Deflate(bytes: ByteArray, offset: Int = 0, size: Int = Int.MAX_VALUE) : ByteArray

    /**
     * Decompress bytes.
     * @param bytes The bytes to decompress.
     * @param offset The offset of the first byte to decompress.
     * @param size The amount of bytes to decompress.
     * @return The inflated (decompress) bytes.
     */
    abstract fun lz4Inflate(bytes: ByteArray, offset: Int = 0, size: Int = Int.MAX_VALUE) : ByteArray

    /**
     * Ask the platform for the given global dictionary.
     */
    abstract fun getGlobalDictionary(id: String): JbDict;
}