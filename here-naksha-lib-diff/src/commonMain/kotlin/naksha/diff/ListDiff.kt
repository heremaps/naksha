@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A difference in two lists.
 * @since 3.0
 */
@JsExport
class ListDiff: AnyObject(), Difference {
    companion object ListDiff_C {
        /**
         * The [PlatformType] of [ListDiff].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ListDiff::class).withPackageName(PACKAGE_NAME).withNameAsJsonType()

        private val DIFFERENCES = NotNullProperty<ListDiff, DifferenceList>(DifferenceList.TYPE) { _, _ -> DifferenceList() }
        private val INT_0 = NotNullProperty<ListDiff, Int>(Int_TYPE) { _, _ -> 0 }
    }

    /**
     * The differences, a list that describes the differences, for each index where something changes a [Difference] is stored, unchanged indices are `null`.
     * @since 3.0
     */
    val differences: DifferenceList by DIFFERENCES

    /**
     * The original length of the list.
     * @since 3.0
     */
    var originalLength: Int by INT_0

    /**
     * The new length of the list, after applying the difference.
     * @since 3.0
     */
    var newLength: Int by INT_0
}