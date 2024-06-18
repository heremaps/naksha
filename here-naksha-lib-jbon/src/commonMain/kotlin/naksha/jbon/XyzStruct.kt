@file:OptIn(ExperimentalJsExport::class)
package naksha.jbon

import naksha.base.BinaryView
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Abstract base class for all XYZ special types.
 * @param binaryView The binary to map initially.
 */
@JsExport
abstract class XyzStruct<SELF : XyzStruct<SELF>>(binaryView: BinaryView) : JbStruct<SELF>(binaryView) {

    @Suppress("OPT_IN_USAGE")
    companion object {
        /**
         * Query a human-readable name of a variant for debugging purpose.
         * @param variant The variant.
         * @return The human-readable name of the variant.
         */
        @JvmStatic
        @JsStatic
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