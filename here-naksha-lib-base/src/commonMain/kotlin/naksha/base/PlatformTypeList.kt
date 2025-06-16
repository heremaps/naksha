@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Standard declaration of a list of [PlatformType].
 * @since
 */
@JsExport
open class PlatformTypeList(): ListProxy<PlatformType<*>>(PlatformType_TYPE) {

    @JsName("AnyPlatformTypeListOf")
    constructor(vararg items: PlatformType<*>) : this() {
        addAll(items)
    }

    companion object AnyPlatformTypeList_C {
        /**
         * The [PlatformType] of [PlatformTypeList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PlatformTypeList::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }

    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: PlatformType<*>?): PlatformTypeList {
        super.add(element)
        return this
    }

}

