@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Standard declaration of a list of [PlatformType].
 * @since
 */
@JsExport
open class AnyPlatformTypeList(): ListProxy<PlatformType<*>>(PlatformType_TYPE) {

    @JsName("AnyPlatformTypeListOf")
    constructor(vararg items: PlatformType<*>) : this() {
        addAll(items)
    }

    companion object AnyPlatformTypeListCompanion {
        /**
         * The [PlatformType] of [AnyPlatformTypeList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyPlatformTypeList::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: PlatformType<*>?): AnyPlatformTypeList {
        super.add(element)
        return this
    }

}

