@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `startsWith` - Tests if a parameter is a `String` and starts with the value.
 *
 * - If the parameter is a list, test each parameter value, and succeed when the first one matches.
 * - If the parameter is a map, tests each parameter key, and succeed when the first one matches.
 * @since 3.0
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class StartsWith() : Check("startsWith") {

    @JsName("StartsWithOf")
    constructor(vararg anyOf: String): this(){
        useAnyOf().addAll(anyOf)
    }

    companion object StartsWith_C {
        /**
         * The [PlatformType] of [StartsWith].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<StartsWith> = forKClass(StartsWith::class).withPackageName(PACKAGE_NAME)
    }

    override fun withAnyOf(vararg any: Any?): StartsWith = super.withAnyOf(*any) as StartsWith
    override fun withAllOf(vararg any: Any?): StartsWith = super.withAllOf(*any) as StartsWith
    override fun withIgnoreCase(ignore: Boolean): StartsWith = super.withIgnoreCase(ignore) as StartsWith

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (value !is String) return false
        if (parameter is List<*>) {
            for (p in parameter) {
                if (p is String && p.startsWith(value, ignoreCase)) return true
            }
            return false
        }
        if (parameter is Map<*, *>) {
            for (e in parameter) {
                val key = e.key
                if (key is String && test(key, value)) return true
            }
            return false
        }
        return parameter is String && parameter.startsWith(value, ignoreCase)
    }
}