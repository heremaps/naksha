@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.auth.action.AccessRightsAction
import naksha.auth.attribute.ResourceAttributes
import naksha.auth.check.CheckCompiler
import naksha.base.AbstractListProxy
import naksha.base.AbstractMapProxy
import naksha.base.ObjectProxy
import kotlin.js.JsExport

/**
 * The URM [UserRightsMatrix] as returned by the UPM (User-Permission-Management).
 *
 * Main function of URM is [UserRightsMatrix.matches] that check whether corresponding [AccessRightsMatrix] (ARM)
 * allows the bearer of this URM to perform a given ACTION upon a RESOURCE within the SERVICE.
 *
 * Both URM and ARM are objects nested in specific hierarchy:
 * - Services are defined on top (see 'naksha' in example below)
 * - Services contain Actions (for example 'readFeatures')
 * - Actions are arrays of attribute maps (these maps are [UserRights] in URM and [ResourceAttributes] in ARM)
 *
 * For given URM and ARM, there is a match if:
 * - both contain the same Services
 * - each Service contain corresponding Actions
 * - all corresponding Actions match - which means that [UserAction] must match [AccessRightsAction]
 * - action matching happens in [UserAction.matches], see docs there
 *
 * ```js
 * { // UserRightsMatrix <-> AccessRightsMatrix
 *   "naksha": { // UserRightsService <-> AccessRightsService
 *     "readFeatures": [ // UserRightsAction <-> AccessRightsAction
 *       { // UserRights <-> ResourceAttributes
 *         "id": "prefix-*",        // check for "id": needs to start with 'prefix-' (gets compiled to StartsWithCheck)
 *         "storageId": "storage"   // check for "storageId": must be equal to 'storage' (gets compiled to EqualityCheck)
 *         "tags": [ "t1-*", "t2" ]   // check for "tags": must contain tag that starts with 't1-' and other that is equal to 't2' (ComposedCheck)
 *       }
 *     ]
 *   }
 * }
 * ```
 */
@JsExport
class UserRightsMatrix : AbstractMapProxy<String, ServiceUserRights>(String::class, ServiceUserRights::class) {

    /**
     * URM matches ARM when each service from URM matches corresponding service in ARM
     * Service match is evaluated in [ServiceUserRights.matches]
     */
    fun matches(accessRightsMatrix: AccessRightsMatrix): Boolean {
        return all { (service, userServiceRights) ->
            val resourceAttributes = accessRightsMatrix[service]
            if (userServiceRights == null || resourceAttributes == null) {
                false
            } else {
                userServiceRights.matches(resourceAttributes)
            }
        }
    }

    fun withService(name: String, service: ServiceUserRights): UserRightsMatrix = apply {
        val existing = getAs(name, ServiceUserRights::class)
        if (existing == null) {
            put(name, service)
        } else {
            existing.mergeActionsFrom(service)
        }
    }

    fun useService(name: String): ServiceUserRights =
        getOrCreate<ServiceUserRights, String, UserRightsMatrix>(name, ServiceUserRights::class)
}

@JsExport
class ServiceUserRights : AbstractMapProxy<String, UserAction>(String::class, UserAction::class) {

    /**
     * Service defined in URM matches service from ARM when all actions for given service are matching
     * Action match is evaluated in [UserAction.matches]
     */
    fun matches(serviceAccessRights: ServiceAccessRights): Boolean {
        return all { (actionName, userAction) ->
            val resourceAction = serviceAccessRights[actionName]
            if (userAction == null || resourceAction == null) {
                false
            } else {
                userAction.matches(resourceAction)
            }
        }
    }

    fun withAction(actionName: String, userRightsAction: UserAction) = apply {
        put(actionName, userRightsAction)
    }

    fun mergeActionsFrom(otherService: ServiceUserRights): ServiceUserRights = apply {
        putAll(otherService)
    }
}

@JsExport
class UserAction : AbstractListProxy<UserRights>(UserRights::class) {

    /**
     * If [AccessRightsAction] passed to this function is empty, it is assumed that there is no restriction
     * and user is allowed to perform given action.
     *
     * [UserAction] matches [AccessRightsAction] when for all [ResourceAttributes] that [AccessRightsAction] contain:
     * - [ResourceAttributes] is null or empty (it is assumed then that there are no access restrictions)
     * - at least single [UserRights] matches [ResourceAttributes] which happens in [UserRights.matches]
     */
    fun matches(accessRightsAction: AccessRightsAction<*, *>): Boolean {
        return accessRightsAction.all { resourceAttributes ->
            if (resourceAttributes == null) {
                true
            } else {
                any { rawCheckMap ->
                    if (rawCheckMap == null) {
                        return false
                    }
                    rawCheckMap.matches(resourceAttributes)
                }
            }
        }
    }

    fun withRights(check: UserRights): UserAction = apply {
        add(check)
    }
}

/**
 * [UserRights] represent attribute map of an action ([UserAction]) from URM side.
 * It's corresponding type on ARM side is [ResourceAttributes] that is comapred againts.
 *
 * The difference is, [UserRights] hold checks, while [ResourceAttributes] hold raw values that these checks
 * are being run against.
 */
class UserRights : ObjectProxy() {

    /**
     * [UserRights] matches [ResourceAttributes] when all of it's compiled checks hold true against resource values.
     *
     * For example when dealing with UserRights:
     * ```js
     * {
     *      "foo": "prefix-*",
     *      "bar": "*-suffix,
     *      "fuzz": "buzz"
     * }
     * ```
     * and ResourceAttributes
     * ```js
     * {
     *      "foo": "prefix-ABC",
     *      "bar": "ABD-suffix,
     *      "fuzz": "meh"
     * }
     * ```
     *
     * the matching process will look as follows:
     * 1) the UserRights entries will get compiled to instances of [CompiledCheck] (see: [CheckCompiler.compile])
     * 2) compilation will end up with this kind of map:
     *     ```js
     *     {
     *          "foo": StartsWithCheck("prefix-"),
     *          "bar": EndsWithCheck("-suffix),
     *          "fuzz": EqualsCheck("buzz")
     *     }
     *     ```
     * 3) for each key, value from [ResourceAttributes] will be obtained
     * 4a) if value is missing - the result is `false`
     * 4b) if value is there, the result will be obtained from the check ([CompiledCheck.matches])
     * 5) the match must be positive for every key-value pairs
     *
     * In the example above, the result is `false` because 'fuzz' is not equal to 'meh'
     */
    fun matches(attributes: ResourceAttributes): Boolean {
        if (isEmpty()) {
            return true
        }
        val checkMap = CheckCompiler.compile(this)
        return all { (propertyName, _) ->
            checkMap[propertyName]
                ?.matches(attributes[propertyName])
                ?: false
        }
    }

    fun withPropertyCheck(propertyName: String, rawCheck: Any) = apply {
        set(propertyName, rawCheck)
    }
}
