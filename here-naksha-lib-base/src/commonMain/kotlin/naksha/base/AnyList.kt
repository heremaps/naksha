@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Standard definition of a list that can hold any value.
 * @see DataViewProxy
 * @see AnyList
 * @see AnyMap
 * @see AnyObject
 * @see AnyTypedObject
 * @see AnyTypedIdObject
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

    companion object AnyList_C {
        /**
         * The [PlatformType] of [AnyList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyList::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
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
