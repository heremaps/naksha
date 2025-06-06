@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.base.ListProxy
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [UserRightsFilter].
 *
 * Normally a single [UserRightsFilter] does not sufficiently describe the rights of a user against a certain set of operation parameters. Therefore, multiple filters are combined using a logical _OR_ to allow a larger variation of operation parameters.
 *
 * For example, a user may have the right to execute a `readFeatures` operation for all features with a tag `name=foo` or a tag `name=bar`. In that case the `readFeatures` operation would have two [UserRightsFilter], one with a `tag:name eq "foo"` check, and the another with a `tag:name eq "bar"` check. When [matches] is called against a `readFeatures` operation, it will check if the actual parameters match either the first _OR_ the second filter.
 * @since 3.0
 * @see UserRightsFilter
 */
@JsExport
class UserRightsFilterList : ListProxy<UserRightsFilter>(UserRightsFilter.TYPE) {
    companion object UserRightsFilterListCompanion {
        /**
         * The [PlatformType] of [UserRightsFilterList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<UserRightsFilterList> = forKClass(UserRightsFilterList::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Tests if a user is allowed to execute some operation with the given parameters.
     * @param params The parameters to test against.
     * @return _true_ if the user is allowed to execute the operation against the given parameters; _false_ otherwise.
     * @since 3.0
     */
    fun matches(params: ServiceOpParams): Boolean {
        for (element in this) {
            if (element == null) continue
            if (element.matches(params)) return true
        }
        return false
    }
}
