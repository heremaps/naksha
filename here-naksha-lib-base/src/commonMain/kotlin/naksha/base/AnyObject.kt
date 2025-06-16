package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The map where the key is [String] and the value can be anything. This is basically what objects normally look like.
 * @see DataViewProxy
 * @see AnyList
 * @see AnyMap
 * @see AnyObject
 * @see AnyTypedObject
 * @see AnyTypedIdObject
 */
@Suppress("unused", "OPT_IN_USAGE")
@JsExport
open class AnyObject : MapProxy<String, Any>(String_TYPE, Any_TYPE) {
    companion object AnyObject_C {
        /**
         * The [PlatformType] of [AnyObject].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyObject::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }
}