@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Standard declaration of a list of doubles.
 */
@JsExport
open class DoubleList : ListProxy<Double>(Double_TYPE) {
    companion object DoubleList_C {
        /**
         * The [PlatformType] of [DoubleList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(DoubleList::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }

    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Double?): DoubleList {
        super.add(element)
        return this
    }

}

