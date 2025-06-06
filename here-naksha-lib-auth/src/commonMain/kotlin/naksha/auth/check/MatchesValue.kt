@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `matchesValue` - Tests if the parameter is a `Map<String,Scalar>` or `List<Scalar>`, and if that map or list contains strings matching the given regular expressions in [anyOf] or [allOf].
 * @since 3.0
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class MatchesValue() : Check("matchesValue") {

    @JsName("MatchesValueOf")
    constructor(vararg anyOf: String) : this() {
        useAnyOf().addAll(anyOf)
    }

    companion object MatchesValueCompanion {
        /**
         * The [PlatformType] of [MatchesValue].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<MatchesValue> = forKClass(MatchesValue::class).withPackageName(PACKAGE_NAME)
    }

    override fun withAnyOf(vararg any: Any?): MatchesValue = super.withAnyOf(*any) as MatchesValue
    override fun withAllOf(vararg any: Any?): MatchesValue = super.withAllOf(*any) as MatchesValue
    override fun withIgnoreCase(ignore: Boolean): MatchesValue = super.withIgnoreCase(ignore) as MatchesValue

    private val regexCache: HashMap<String, Regex> = HashMap()

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (value !is String) return false

        var regex = regexCache[value]
        if (regex == null) {
            regex = if (ignoreCase) Regex(value, RegexOption.IGNORE_CASE) else Regex(value)
            regexCache[value] = regex
        }

        if (parameter is Map<*,*>) {
            for (e in parameter) {
                val v = e.value
                if (v is String && regex.matches(v)) return true
            }
        } else if (parameter is List<*>) {
            for (v in parameter) {
                if (v is String && regex.matches(v)) return true
            }
        }
        return false
    }
}
