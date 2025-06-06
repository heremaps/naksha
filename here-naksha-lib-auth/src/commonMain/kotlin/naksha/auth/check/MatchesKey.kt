@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * `matchesKey` - Tests if the parameter is a `Map<String,Scalar>`, and if that map contains keys matching the given regular expressions in [anyOf] or [allOf].
 * @since 3.0
 * @see Check
 * @see naksha.auth.ServiceOpParams
 */
@JsExport
class MatchesKey() : Check("matchesKey") {

    @JsName("MatchesKeyOf")
    constructor(vararg anyOf: String) : this() {
        useAnyOf().addAll(anyOf)
    }

    companion object MatchesKeyCompanion {
        /**
         * The [PlatformType] of [MatchesKey].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<MatchesKey> = forKClass(MatchesKey::class).withPackageName(PACKAGE_NAME)
    }

    override fun withAnyOf(vararg any: Any?): MatchesKey = super.withAnyOf(*any) as MatchesKey
    override fun withAllOf(vararg any: Any?): MatchesKey = super.withAllOf(*any) as MatchesKey
    override fun withIgnoreCase(ignore: Boolean): MatchesKey = super.withIgnoreCase(ignore) as MatchesKey

    private val regexCache: HashMap<String, Regex> = HashMap()

    override fun test(parameter: Any?, value: Any?): Boolean {
        if (value !is String || parameter !is Map<*, *>) return false
        var regex = regexCache[value]
        if (regex == null) {
            regex = if (ignoreCase) Regex(value, RegexOption.IGNORE_CASE) else Regex(value)
            regexCache[value] = regex
        }
        for (key in parameter.keys) {
            if (key is String && regex.matches(key)) return true
        }
        return false
    }
}
