@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.AccessRightsMatrix
import com.here.naksha.lib.auth.ServiceAccessRights
import com.here.naksha.lib.auth.action.AccessRightsAction
import com.here.naksha.lib.auth.attribute.ResourceAttributes
import com.here.naksha.lib.auth.check.CheckCompiler
import naksha.base.P_List
import naksha.base.P_Map
import naksha.base.P_Object
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
 *       // CheckMap, starts with "foo-" or "bar-" AND ends with "-fn" *       {"id": {"startsWith":["foo-", "bar-"], "endsWith":["-fn"]}}
 *     ]
 *   }
 * }
 * ```
 */
@JsExport
class UserRightsMatrix : P_Map<String, ServiceUserRights>(String::class, ServiceUserRights::class) {

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

    fun getService(name: String): ServiceUserRights =
        getOrCreate(name, ServiceUserRights::class)
}

@JsExport
class ServiceUserRights : P_Map<String, UserAction>(String::class, UserAction::class) {

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
class UserAction : P_List<UserRights>(UserRights::class) {

    /**
     * User Action matches resource's Access Action when EVERY attribute on resource side matches
     * at least one User Rights.
     * Matching between resource's attributes and user's rights happens in [UserRights.matches]
     *
     * TODO:
     * User Rights match ResourceAttributes if for every KEY from (KEY, CHECK) entries of User Rights,
     * there is a corresponding (KEY, VALUE) entry in Resource Attributes where KEY is the same and
     * compiled CHECK returns true for given VALUE.
     *
     * If User Rights are empty, it is assumed that the match is positive.
     * The reasoning is, that empty user-rights are the biggest rights, because the user is not limited!
     *
     * Please note that User Rights hold raw (not compiled) Checks. Compilation process happens within
     * [UserRights.matches] method and proceeds final [CheckMap.matches] step
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

// attribute map from user's perspective
class UserRights : P_Object() {

    fun matches(attributes: ResourceAttributes): Boolean {
        if (isEmpty()) {
            return true
        }
        val checkMap = CheckCompiler.compile(this)
        return all { (propertyName, _) ->
            val res = checkMap[propertyName]
                ?.matches(attributes[propertyName])
                ?: false
            println("Check for $propertyName: $res")
            res
        }
    }

    fun withPropertyCheck(propertyName: String, rawCheck: Any) = apply {
        set(propertyName, rawCheck)
    }
}
