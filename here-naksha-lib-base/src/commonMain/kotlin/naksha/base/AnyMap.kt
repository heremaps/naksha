@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A standard definition of a map that can have any key and value.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@JsExport
open class AnyMap : MapProxy<Any, Any>(Any_TYPE, Any_TYPE) {
    companion object AnyMapCompanion {
        /**
         * The [PlatformType] of [AnyMap].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<AnyMap> = forKClass(AnyMap::class).withPackageName(PACKAGE_NAME)
    }
}