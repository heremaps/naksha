@file:OptIn(ExperimentalJsExport::class)
package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic
import kotlin.math.exp

/**
 * Abstract base class for all XYZ special types.
 */
@JsExport
abstract class XyzStruct<SELF : XyzStruct<SELF>> : JbStruct<SELF>() {

    companion object {
        @JvmStatic
        fun xyzVariantName(variant: Int?) : String {
            return when(variant) {
                null -> "struct-xyz-null"
                XYZ_NS_VARIANT -> "struct-xyz-namespace"
                XYZ_OPS_VARIANT -> "struct-xyz-ops"
                XYZ_TAGS_VARIANT -> "struct-xyz-tags"
                else -> "struct-xyz-${variant}"
            }
        }
    }

    open fun parseXyzHeader(expectedVariant: Int) {
        check(unitType == TYPE_XYZ) { "Mapped structure is no XYZ structure, but ${JbReader.unitTypeName(unitType)}" }
        check(variant == expectedVariant) {"Mapping XYZ variant ${xyzVariantName(expectedVariant)}, but found ${xyzVariantName(variant)}"}
    }

}