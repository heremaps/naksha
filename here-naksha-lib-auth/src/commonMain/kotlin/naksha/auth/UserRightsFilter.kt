@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.auth.check.Check
import naksha.base.*
import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Describes the limits a user has when executing a certain operation.
 *
 * For each parameter of an operation, this filter may contain a check condition:
 * - For those parameters the user has full access the corresponding key in this map is `undefined`, therefore this map is empty, when the user has full access to the operation _(it is allowed to execute it with arbitrary parameters)_.
 * - When this rights described limited access to a resource, one or more keys are assigned to a check to verify each limit. For example, a user may only have access to resources with a specific _name_, then there would be a key `name` assigned to a check describing this limit, e.g. [equals][naksha.auth.check.Equals] the string "foo". The formal notation for the check is `name eq "foo"`.
 * - All checks being part of a single resource-rights map must validate to _true_ for the rights to evaluated to _true_, so for the actor to have access. This means, if the map is empty, access is always granted to any resource.
 *
 * **Summary**: The key-value pairs of resource-rights form a logical _AND_, only when all checks pass, the access is granted.
 * @since 3.0
 * @see UserRightsFilterList
 */
@JsExport
class UserRightsFilter : MapProxy<String, Check>(String_TYPE, Check.TYPE) {

    companion object UserRightsFilterCompanion {
        /**
         * The [PlatformType] of [UserRightsFilter].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<UserRightsFilter> = forKClass(UserRightsFilter::class).withPackageName(PACKAGE_NAME)
    }

    override fun toValue(key: String, value: Any?, alt: Check?): Check? = Check.forAny(value)

    /**
     * Tests if a user is allowed to execute this operation with all given parameters.
     * @param params All parameters with which the operations should be executed.
     * @return _true_ if the user is allowed to execute this operation with all given parameters; _false_ otherwise.
     * @since 3.0
     */
    fun matches(params: ServiceOpParams): Boolean {
        for (key in this.keys) {
            val check = Check.forAny(getRaw(key))
            if (!check.matches(params[key])) return false
        }
        return true
    }
}