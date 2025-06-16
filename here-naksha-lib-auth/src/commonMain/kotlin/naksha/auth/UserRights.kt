@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A map where the key is the name of the operation for which the user does have some rights, and the value is a [list of filters][UserRightsFilter], describing with which parameters the user may execute the operation.
 *
 * The user does not have any rights to operations not being part of this map. Therefore, to execute a certain operation, the user-rights need to contain at least the key of the operation, and as value a [filter list][UserRightsFilterList] with at least one entry.
 *
 * This object is normally extracted for a specific service from the [UserRightsMatrix], used to test if all operations the user wants to execute are allowed.
 *
 * @since 3.0
 * @see matches
 * @see ServiceOps
 * @see UserRightsMatrix
 */
@JsExport
class UserRights : MapProxy<String, UserRightsFilterList>(String_TYPE, UserRightsFilterList.TYPE) {
    companion object UserRights_C {
        /**
         * The [PlatformType] of [UserRights].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<UserRights> = forKClass(UserRights::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Tests if a user is allowed to execute a set of operations.
     * @param operations The operations that a user wants to execute.
     * @return _true_ if the user is allowed to execute all the operations; _false_ otherwise.
     * @since 3.0
     * @see ServiceOps
     * @see UserRightsFilter
     */
    fun matches(operations: ServiceOps): Boolean = _matches(operations, true)

    /**
     * Tests if the user has the right to execute the given operations, without testing any parameters.
     *
     * If the service, application or library does not yet exactly know the parameters of an operation, this method can be helpful. For example, if the user wants to perform a `readFeatures` operation, the pre-check will be able to test, if the user at least has any rights to read features, without checking the parameters. This allows the application, service, or library to create a blank [ServiceOps] map, where the values are all empty lists, and then to test, if the user is generally even allowed to so some reading. This prevents that a read is executed for a user that does not even have the right to read anything.
     *
     * @param operations The operations the user wants to execute.
     * @return _true_ if the user has the right to execute the given operations, ignoring the parameters; _false_ otherwise.
     * @since 3.0
     */
    fun preCheck(operations: ServiceOps): Boolean = _matches(operations, false)

    private fun _matches(operations: ServiceOps, withParameters: Boolean): Boolean {
        for (op in operations.keys) {
            val paramsList = operations[op] ?: continue // null = No parameter-sets, no executions!
            if (paramsList !is ListProxy<*>) throw illegalArg("The operation '$op' has an invalid parameter-set list")
            if (paramsList.isEmpty()) continue // Empty parameter-sets, no executions!

            val rights = this[op]
            if (rights.isNullOrEmpty()) return false // User has no rights for the operation
            if (withParameters) {
                for (i in 0 until paramsList.size) {
                    val any = paramsList[i]
                    if (any !is MapProxy<*, *>) {
                        throw illegalArg("The operation '$op' has an invalid parameter-set at index $i, needs to be a map")
                    }
                    val params = any.proxy(ServiceOpParams.TYPE)
                    if (!rights.matches(params)) return false
                }
            }
        }
        // If all tests passed, we can grant access.
        return true
    }

}