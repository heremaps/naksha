@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `contains` - Tests if a parameter is a `String` and contains the value. If the parameter is a list, test each parameter and succeed when the first one of the parameters contains with the value.
 * @since 3.0
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class Contains() : Check("contains") {

    @JsName("ContainsOf")
    constructor(vararg anyOf: String) : this() {
        useAnyOf().addAll(anyOf)
    }

    companion object Contains_C {
        /**
         * The [PlatformType] of [Contains].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<Contains> = forKClass(Contains::class).withPackageName(PACKAGE_NAME)
    }

    override fun withAnyOf(vararg any: Any?): Contains = super.withAnyOf(*any) as Contains
    override fun withAllOf(vararg any: Any?): Contains = super.withAllOf(*any) as Contains
    override fun withIgnoreCase(ignore: Boolean): Contains = super.withIgnoreCase(ignore) as Contains

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (value !is String) return false
        if (parameter is List<*>) {
            for (p in parameter) {
                if (p is String && p.contains(value, ignoreCase)) return true
            }
            return false
        }
        return parameter is String && parameter.contains(value, ignoreCase)
    }
}