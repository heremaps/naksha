@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Standard definition of a list that can hold any value.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@JsExport
open class AnyList() : ListProxy<Any>(Any_TYPE) {

    @JsName("AnyListOf")
    constructor(vararg items: Any?) : this() {
        @Suppress("UselessCallOnNotNull")
        if (!items.isNullOrEmpty()) {
            setCapacity(items.size)
            for (item in items) {
                this.add(item)
            }
        }
    }

    companion object AnyListCompanion {
        /**
         * The [PlatformType] of [AnyList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<AnyList> = forKClass(AnyList::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Any?): AnyList {
        add(element)
        return this
    }

    /**
     * Adds all given elements to the end of this list.
     * @param element the elements to add.
     * @return this.
     * @since 3.0
     */
    fun appendAll(vararg element: Any?): AnyList {
        addAll(element)
        return this
    }

}
