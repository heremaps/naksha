@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.AccessRightsMatrix
import com.here.naksha.lib.auth.AccessRightsService
import com.here.naksha.lib.auth.action.AccessRightsAction
import com.here.naksha.lib.auth.attribute.ResourceAttributes
import com.here.naksha.lib.auth.check.CheckMap
import com.here.naksha.lib.auth.check.CheckMapCompiler
import naksha.base.P_List
import naksha.base.P_Map
import kotlin.js.JsExport

/**
 * The URM (User-Rights-Matrix) as returned by the UPM (User-Permission-Management).
 *
 * ```js
 * { // UserRightsMatrix <-> AccessRightsMatrix
 *   "naksha": { // UserRightsService <-> AccessRightsService
 *     "readFeatures": [ // UserRightsAction <-> AccessRightsAction
 *       { // UserRights <-> ResourceAttributes
 *         "id": { // CheckMap, compiled from {"id": "x-*"}
 *           "startsWith": [ // Check
 *             "x-"
 *           ]
 *         }
 *        },
 *
 *       // CheckMap, direct syntax, no short alternative
 *       {"id": {"anyOf":["foo", "bar"]}}
 *
 *       // CheckMap, starts with "foo-" or "bar-" AND ends with "-fn"
 *       {"id": {"startsWith":["foo-", "bar-"], "endsWith":["-fn"]}}
 *     ]
 *   }
 * }
 * ```
 */
@JsExport
class UserRightsMatrix : P_Map<String, UserRightsService>(String::class, UserRightsService::class) {

    /**
     * URM matches ARM when each service from URM matches corresponding service in ARM
     * Service match is evaluated in [UserRightsService.matches]
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
}

@JsExport
class UserRightsService : P_Map<String, UserRightsAction>(String::class, UserRightsAction::class) {

    /**
     * Service defined in URM matches service from ARM when all actions for given service are matching
     * Action match is evaluated in [UserRightsAction.matches]
     */
    fun matches(accessRightsService: AccessRightsService): Boolean {
        return all { (actionName, userAction) ->
            val resourceAction = accessRightsService[actionName]
            if (userAction == null || resourceAction == null) {
                false
            } else {
                userAction.matches(resourceAction)
            }
        }
    }
}

@JsExport
class UserRightsAction : P_List<UserRights>(UserRights::class) {

    /**
     * User Action matches resource's Access Action when EVERY attribute on resource side matches
     * at least one User Rights.
     * Matching between resource's attributes and user's rights happens in [UserRights.matches]
     */
    fun matches(accessRightsAction: AccessRightsAction<*, *>): Boolean {
        return accessRightsAction.all { resourceAttributes ->
            if (resourceAttributes == null) {
                return false
            }
            any { userRights ->
                if (userRights == null) {
                    return false
                }
                userRights.matches(resourceAttributes)
            }
        }
    }
}


@JsExport
class UserRights : P_Map<String, RawCheckMap>(String::class, RawCheckMap::class) {
    /**
     * User Rights match ResourceAttributes if for every KEY from (KEY, CHECK) entries of User Rights,
     * there is a corresponding (KEY, VALUE) entry in Resource Attributes where KEY is the same and
     * compiled CHECK returns true for given VALUE.
     *
     * If User Rights are empty, it is assumed that the match is positive.
     * The reasoning is, that empty user-rights are the biggest rights, because the user is not limited!
     *
     * Please note that User Rights hold raw (not compiled) Checks. Compilation process happens within
     * [RawCheckMap.matches] method and proceeds final [CheckMap.matches] step
     */
    fun matches(attributes: ResourceAttributes): Boolean {
        if (isEmpty()) {
            return true
        }
        return all { (propertyName, rawCheckMap) ->
            if (rawCheckMap == null) {
                return false
            }
            rawCheckMap.matches(attributes[propertyName])
        }
    }
}


class RawCheckMap : P_Map<String, Any>(String::class, Any::class) {

    fun matches(value: Any?): Boolean {
        return CheckMapCompiler
            .compile(this)
            .matches(value)
    }
}
