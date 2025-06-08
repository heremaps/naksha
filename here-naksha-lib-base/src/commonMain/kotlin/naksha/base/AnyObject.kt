package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The map where the key is [String] and the value can be anything. This is basically what objects normally look like.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@Suppress("unused", "OPT_IN_USAGE")
@JsExport
open class AnyObject : MapProxy<String, Any>(String_TYPE, Any_TYPE) {
    companion object AnyObjectCompanion {
        /**
         * The [PlatformType] of [AnyObject].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyObject::class).withPackageName(PACKAGE_NAME)
    }
}