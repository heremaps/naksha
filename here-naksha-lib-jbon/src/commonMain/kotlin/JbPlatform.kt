@file:OptIn(ExperimentalJsExport::class)

// TODO: Extend JbPlatform with some native methods exposure:
// Arrays and Maps:
// newArray : Any
// newMap : Any
// getInt(map:Any, key:String)
// getInt(array:Any, index:int)
// ...
// We do this, to use native JSON parser and serializer
// This allows to directly access JSONB in plv8, JSON object in the browser and Jackson tree building

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The API to be provided by the platform.
 */
@JsExport
abstract class JbPlatform {
    companion object {
        /**
         * The reference to the platform instance set by the platform and read via [get].
         */
        internal var instance: JbPlatform? = null

        /**
         * Returns the platform instance.
         */
        fun get(): JbPlatform {
            return instance as JbPlatform
        }
    }

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
     * Creates a view above the given byte-array.
     * @param bytes The byte-array for which to create a view.
     * @param offset The offset into the byte-array to map.
     * @param size The amount of byte to map, if longer than the byte-array, till the end of the byte-array.
     * @return The view to the byte-array.
     */
    abstract fun newDataView(bytes: ByteArray, offset: Int = 0, size: Int = Int.MAX_VALUE): IDataView

    /**
     * Create a new empty JBON builder of default size, which normally suites the most JSONs.
     * @param dict The global dictionary to use, if any.
     * @return A new JBON builder, normally good enough for most JSON objects.
     */
    fun newBuilder(dict: JbDict? = null): JbBuilder {
        val buffer = ByteArray(32768) // we expect that 32kb are enough for all binary JSONs!
        return JbBuilder(newDataView(buffer), dict)
    }
}