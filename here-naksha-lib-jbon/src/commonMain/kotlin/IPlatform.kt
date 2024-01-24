@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The API to be provided by the platform.
 */
@JsExport
interface IPlatform {
    /**
     * Creates a view above the given byte-array.
     * @param bytes The byte-array for which to create a view.
     * @param offset The offset into the byte-array to map.
     * @param size The amount of byte to map, if longer than the byte-array, till the end of the byte-array.
     * @return The view to the byte-array.
     */
    fun dataViewOf(bytes: ByteArray, offset: Int = 0, size: Int = Int.MAX_VALUE): IDataView

    /**
     * Create a default JBON builder that normally suites the most JSONs we encounter.
     * @param dict The global dictionary to use, if any.
     * @return A default JBON builder that normally is good enough for most JSON objects.
     */
    fun jbonBuilder(dict : JbonDict? = null) : JbonBuilder {
        val buffer = ByteArray(32768) // we expect that 32kb are enough for all binary JSONs!
        return JbonBuilder(dataViewOf(buffer), dict)
    }
}