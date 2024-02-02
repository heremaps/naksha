@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The native API to be provided by the platform to grant access to native capabilities for this session.
 */
@Suppress("unused")
@JsExport
abstract class JbSession {
    companion object {
        /**
         * The reference to the thread local session getter.
         */
        internal var instance: IJbThreadLocalSession? = null

        /**
         * Returns the thread-local session.
         * @return Returns the thread-local session.
         */
        fun get(): JbSession {
            return instance!!.get()
        }
    }

    /**
     * The name of the application using this session.
     */
    var appName: String? = null

    /**
     * The application identifier of the application using this session.
     */
    var appId: String? = null

    /**
     * The author using this session.
     */
    var author: String? = null

    /**
     * The stream-id (used for logging and debugging) of session.
     */
    var streamId: String? = null

    /**
     * Returns the API that grants access to native maps.
     * @return The API that grants access to native maps.
     */
    abstract fun map(): INativeMap

    /**
     * Returns the API that grants access to native lists.
     * @return The API that grants access to native lists.
     */
    abstract fun list(): INativeList

    /**
     * Returns the API that grants access to the native SQL engine.
     * @return The API that grants access to the native SQL engine.
     */
    abstract fun sql(): ISql

    /**
     * Returns the native logging API.
     * @return The native logging API.
     */
    abstract fun log(): INativeLog

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
     * This is more for platform code, to create new byte-arrays the same way as Kotlin does it. For JAVA this
     * means to create a `byte[]`, for JavaScript it means a `Int8Array`. Beware, that technically the `Int8Array`
     * is already a view using a buffer beneath, which actually is the real byte-array.
     * @param size The size of the byte-array.
     * @return The new byte array.
     */
    fun newByteArray(size: Int): ByteArray {
        return ByteArray(size)
    }

    /**
     * Creates a new JBON builder.
     * @param globalDict The global dictionary to use for this builder.
     * @param size The size of the builder buffer.
     * @return a new builder.
     */
    fun newBuilder(globalDict: JbDict? = null, size: Int = 65536): JbBuilder {
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

    internal fun endOf(bytes: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0 || size >= bytes.size) {
            return bytes.size
        }
        val end = offset + size
        if (end > bytes.size) {
            return bytes.size
        }
        return end
    }

    /**
     * Compress bytes.
     * @param raw The bytes to compress.
     * @param offset The offset of the first byte to compress.
     * @param size The amount of bytes to compress.
     * @return The deflated (compressed) bytes.
     */
    abstract fun lz4Deflate(raw: ByteArray, offset: Int = 0, size: Int = Int.MAX_VALUE): ByteArray

    /**
     * Decompress bytes.
     * @param compressed The bytes to decompress.
     * @param bufferSize The amount of bytes that are decompressed, if unknown, set 0.
     * @param offset The offset of the first byte to decompress.
     * @param size The amount of bytes to decompress.
     * @return The inflated (decompress) bytes.
     */
    abstract fun lz4Inflate(compressed: ByteArray, bufferSize: Int = 0, offset: Int = 0, size: Int = Int.MAX_VALUE): ByteArray

    /**
     * Ask the platform for the given global dictionary.
     */
    abstract fun getGlobalDictionary(id: String): JbDict;
}