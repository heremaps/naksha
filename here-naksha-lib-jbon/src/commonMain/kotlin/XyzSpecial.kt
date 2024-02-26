@file:OptIn(ExperimentalJsExport::class)
package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Abstract base class for all XYZ special types.
 */
@JsExport
abstract class XyzSpecial<SELF : XyzSpecial<SELF>> : JbStructMapper<SELF>() {
    internal var variant = 0

    override fun parseHeader(mandatory: Boolean) {
        // Header parsing is always mandatory for features!
        check(reader.unitType() == TYPE_XYZ)
        check(reader.enterUnit())
        // Read XYZ variant.
        check(reader.isInt())
        variant = reader.readInt32()
        check(reader.nextUnit())
    }

    /**
     * Returns the XYZ variant.
     */
    fun variant() : Int = variant
}