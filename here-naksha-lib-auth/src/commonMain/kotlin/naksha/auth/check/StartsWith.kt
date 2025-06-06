@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `startsWith` - Tests if a parameter is a `String` and starts with the value. If the parameter is a list, test each parameter and succeed when the first one of the parameters starts with the value.
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

    companion object StartsWithCompanion {
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
        return parameter is String && parameter.startsWith(value, ignoreCase)
    }
}