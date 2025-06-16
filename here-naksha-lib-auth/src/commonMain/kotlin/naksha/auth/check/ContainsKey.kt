@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `containsKey` - Tests if a parameter is a `Map<String,Scalar>`, and if that map contains keys matching the given strings in [anyOf] or [allOf].
 * @since 3.0
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class ContainsKey() : Check("containsKey") {

    @JsName("ContainsKeyOf")
    constructor(vararg anyOf: String) : this() {
        useAnyOf().addAll(anyOf)
    }

    companion object ContainsKey_C {
        /**
         * The [PlatformType] of [ContainsKey].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<ContainsKey> = forKClass(ContainsKey::class).withPackageName(PACKAGE_NAME)
    }

    override fun withAnyOf(vararg any: Any?): ContainsKey = super.withAnyOf(*any) as ContainsKey
    override fun withAllOf(vararg any: Any?): ContainsKey = super.withAllOf(*any) as ContainsKey
    override fun withIgnoreCase(ignore: Boolean): ContainsKey = super.withIgnoreCase(ignore) as ContainsKey

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (value !is String || parameter !is Map<*, *>) return false
        return parameter.containsKey(value)
    }
}
