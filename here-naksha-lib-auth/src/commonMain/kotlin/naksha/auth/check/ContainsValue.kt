@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `containsValue` - Tests if a parameter is a `Map<String,Scalar>` or a `List<Scalar>`, and if that map or list contains values matching the given ones in [anyOf] or [allOf].
 * @since 3.0
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class ContainsValue() : Check("containsValue") {

    @JsName("ContainsValueOf")
    constructor(vararg anyOf: String) : this() {
        useAnyOf().addAll(anyOf)
    }

    companion object ContainsValue_C {
        /**
         * The [PlatformType] of [ContainsValue].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<ContainsValue> = forKClass(ContainsValue::class).withPackageName(PACKAGE_NAME)
    }

    override fun withAnyOf(vararg any: Any?): ContainsValue = super.withAnyOf(*any) as ContainsValue
    override fun withAllOf(vararg any: Any?): ContainsValue = super.withAllOf(*any) as ContainsValue
    override fun withIgnoreCase(ignore: Boolean): ContainsValue = super.withIgnoreCase(ignore) as ContainsValue

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (value is List<*> || value is Map<*,*>) return false
        if (parameter is Map<*, *>) {
            for (e in parameter) {
                if (e.value == value) return true
            }
        } else if (parameter is List<*>) {
            for (v in parameter) {
                if (v == value) return true
            }
        }
        return false
    }
}
