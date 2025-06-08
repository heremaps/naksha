@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Standard declaration of a list of 64-bit integers.
 */
@JsExport
open class Int64List : ListProxy<Int64>(Int64_TYPE) {

    companion object Int64ListCompanion {
        /**
         * The [PlatformType] of [Int64List].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Int64List::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Int64?): Int64List {
        super.add(element)
        return this
    }

}

