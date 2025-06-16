@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A standard definition of a map that can have any key and any value _(`Map<Any,Any>`)_.
 *
 * @since 3.0
 * @see DataViewProxy
 * @see AnyList
 * @see AnyMap
 * @see AnyObject
 * @see AnyTypedObject
 * @see AnyTypedIdObject
 */
@JsExport
open class AnyMap : MapProxy<Any, Any>(Any_TYPE, Any_TYPE) {
    companion object AnyMap_C {
        /**
         * The [PlatformType] of [AnyMap].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyMap::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }
}