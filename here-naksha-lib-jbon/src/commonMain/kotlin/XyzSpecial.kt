@file:OptIn(ExperimentalJsExport::class)
package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Abstract base class for all XYZ special types.
 */
@JsExport
abstract class XyzSpecial<SELF : XyzSpecial<SELF>> : JbObjectMapper<SELF>() {
    internal var variant = 0

    override fun parseHeader(mandatory: Boolean) {
        // Header parsing is always mandatory for features!
        check(reader.unitType() == TYPE_XYZ)
        // Total size of feature.
        reader.addOffset(1)
        check(reader.isInt())
        variant = reader.readInt32()
        check(reader.nextUnit())
    }

    /**
     * Returns the XYZ variant.
     */
    fun variant() : Int = variant
}