@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `matches` - Tests if a parameter is a `String` and matches the value, which should be a regular expression. If the parameter is a list, test each parameter and succeed when the first one of the parameters matches with the regex.
 * @since 3.0
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class Matches() : Check("matches") {

    @JsName("MatchesOf")
    constructor(vararg anyOf: String) : this() {
        useAnyOf().addAll(anyOf)
    }

    companion object MatchesCompanion {
        /**
         * The [PlatformType] of [Matches].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<Matches> = forKClass(Matches::class).withPackageName(PACKAGE_NAME)
    }

    override fun withAnyOf(vararg any: Any?): Matches = super.withAnyOf(*any) as Matches
    override fun withAllOf(vararg any: Any?): Matches = super.withAllOf(*any) as Matches
    override fun withIgnoreCase(ignore: Boolean): Matches = super.withIgnoreCase(ignore) as Matches

    private val regexCache: HashMap<String, Regex> = HashMap()

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (value !is String) return false

        var regex = regexCache[value]
        if (regex == null) {
            regex = if (ignoreCase) Regex(value, RegexOption.IGNORE_CASE) else Regex(value)
            regexCache[value] = regex
        }

        if (parameter is List<*>) {
            for (p in parameter) {
                if (p is String && regex.matches(p)) return true
            }
            return false
        }
        return parameter is String && regex.matches(parameter)
    }
}