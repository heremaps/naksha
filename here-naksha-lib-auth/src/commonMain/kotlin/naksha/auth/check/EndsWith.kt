@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `endsWidth` - Tests if a parameter is a `String` and ends with the value. If the parameter is a list, test each parameter and succeed when the first one of the parameter ends with the value.
 * @since 3.0
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class EndsWith() : Check("matches") {

    @JsName("EndsWithOf")
    constructor(vararg anyOf: String) : this() {
        useAnyOf().addAll(anyOf)
    }

    companion object EndsWith_C {
        /**
         * The [PlatformType] of [EndsWith].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<EndsWith> = forKClass(EndsWith::class).withPackageName(PACKAGE_NAME)
    }

    override fun withAnyOf(vararg any: Any?): EndsWith = super.withAnyOf(*any) as EndsWith
    override fun withAllOf(vararg any: Any?): EndsWith = super.withAllOf(*any) as EndsWith
    override fun withIgnoreCase(ignore: Boolean): EndsWith = super.withIgnoreCase(ignore) as EndsWith

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (value !is String) return false
        if (parameter is List<*>) {
            for (p in parameter) {
                if (p is String && p.endsWith(value, ignoreCase)) return true
            }
            return false
        }
        return parameter is String && parameter.endsWith(value, ignoreCase)
    }
}