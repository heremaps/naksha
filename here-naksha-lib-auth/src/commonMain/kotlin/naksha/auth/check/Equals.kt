@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformUtil.PlatformUtil_C.asSafeDouble
import naksha.base.PlatformUtil.PlatformUtil_C.asSafeInt
import naksha.base.PlatformUtil.PlatformUtil_C.asSafeInt64
import naksha.base.PlatformUtil.PlatformUtil_C.isLogicalDouble
import naksha.base.PlatformUtil.PlatformUtil_C.isLogicalInt
import naksha.base.PlatformUtil.PlatformUtil_C.isLogicalInt64
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `eq` - Tests if a parameter equals the value. Only supports `Scalar` values, so _null_, _boolean_, _string_, _int_, _int64_, or _double_.
 *
 * - If the parameter is a list, test each parameter value, and succeed when the first one matches.
 * - If the parameter is a map, tests each parameter key, and succeed when the first one matches.
 *
 * @since 3.0
 * @see strict
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class Equals() : Check("eq") {

    @JsName("EqualsOf")
    constructor(vararg anyOf: Any?): this(){
        useAnyOf().addAll(anyOf)
    }

    companion object Equals_C {
        /**
         * The [PlatformType] of [Equals].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<Equals> = forKClass(Equals::class).withPackageName(PACKAGE_NAME)

        private val BOOLEAN_MEMBER = NotNullProperty<Equals, Boolean>(Boolean_TYPE) { _, _ -> false }
    }

    override fun withAnyOf(vararg any: Any?): Equals = super.withAnyOf(*any) as Equals
    override fun withAllOf(vararg any: Any?): Equals = super.withAllOf(*any) as Equals
    override fun withIgnoreCase(ignore: Boolean): Equals = super.withIgnoreCase(ignore) as Equals

    /**
     * If the test should be performed strict.
     *
     * Strict checks change the way numbers are tested. When enabled _(true)_, numbers will be compared type-safe, which means that `1.0` _(double)_ will not equal `1` _(int)_.
     * @since 3.0
     */
    var strict: Boolean by BOOLEAN_MEMBER

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (parameter is List<*>) {
            for (p in parameter) {
                if (test(p, value)) return true
            }
            return false
        }
        if (parameter is Map<*, *>) {
            if (value !is String) return false
            for (e in parameter) {
                val key = e.key
                if (key is String && test(key, value)) return true
            }
            return false
        }

        // Note: When 'ignoreCase' is true, this forces the equals to be string based!
        if (ignoreCase) return parameter is String && matchesString(parameter, value)
        if (strict) return parameter == value
        if (parameter is String) return matchesString(parameter, value)
        if (parameter is Boolean) return matchesBoolean(parameter, value)
        if (isLogicalInt(parameter)) {
            val p = asSafeInt(parameter) ?: throw illegalState("Internal error while processing integer")
            return matchesInt(p, value)
        }
        if (isLogicalInt64(parameter)) {
            val p = asSafeInt64(parameter) ?: throw illegalState("Internal error while processing 64-bit integer")
            return matchesInt64(p, value)
        }
        if (isLogicalDouble(parameter)) {
            val p = asSafeDouble(parameter) ?: throw illegalState("Internal error while processing floating point number")
            return matchesDouble(p, value)
        }
        return false
    }

    private fun matchesBoolean(parameter: Boolean, value: Any?): Boolean
        = value is Boolean && value == parameter

    private fun matchesString(parameter: String, value: Any?): Boolean
        = value is String && value.equals(parameter, ignoreCase)

    private fun matchesInt(parameter: Int, value: Any?): Boolean {
        val v = asSafeInt(value) ?: return false
        return v == parameter
    }

    private fun matchesInt64(parameter: Int64, value: Any?): Boolean {
        val v = asSafeInt64(value) ?: return false
        return v == parameter
    }

    private fun matchesDouble(parameter: Double, value: Any?): Boolean {
        val v = asSafeDouble(value) ?: return false
        return v == parameter
    }

}