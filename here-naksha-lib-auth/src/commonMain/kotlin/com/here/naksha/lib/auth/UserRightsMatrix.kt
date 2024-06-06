@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.AccessRightsAction
import com.here.naksha.lib.auth.attribute.ResourceAttributes
import com.here.naksha.lib.auth.attribute.AccessRightsService
import com.here.naksha.lib.auth.check.*
import com.here.naksha.lib.auth.AccessRightsMatrix
import com.here.naksha.lib.base.*
import kotlin.js.JsExport
import kotlin.reflect.KClass

class CheckMap : P_Map<String, Check>(String::class, UnknownOp::class) {
    companion object {
        /**
         * All registered operations.
         */
        private val all = HashMap<String, KClass<out Check>>() // TODO: Needs to be a concurrent hash map, add ConcurrentMap to lib-base!

        init {
            all[EqualsCheck.NAME] = EqualsCheck::class
            all[StartsWithCheck.NAME] = StartsWithCheck::class
            all[EndsWithCheck.NAME] = EndsWithCheck::class
        }
    }

    // Overriding this method will convert the values into individual instances, based upon the key.
    // The key is something like "equals", "startsWith", "endsWith", ...
    override fun toValue(key: String, value: Any?, alt: Check?): Check? {
        val opKlass = all[key] ?: return super.toValue(key, value, alt)
        return box(value, opKlass)
    }

    /**
     * Test all check-operations against the attribute value.
     * @param value The attribute value as read from [ResourceAttributes].
     * @return _true_ if all operations match; _false_ otherwise.
     */
    fun matches(value: Any?): Boolean {
        for (opName in keys) { // someOp, equals, ...
            val op = get(opName)
            if (op == null) {
                // TODO: Log an info and the operation is invalid (with the name)
                return false
            }
            if (op is UnknownOp) {
                // TODO: Log an info that we found an unknown op (with the name)
                return false
            }
            if (!op.matches(value)) return false
        }
        return true
    }
}

@JsExport
class UserRights : P_Map<String, CheckMap>(String::class, CheckMap::class) {
    fun matches(attributes: ResourceAttributes): Boolean {
       TODO("Compare each attribute of the given resource against the corresponding check-map")
       // If no check-map exists, this means that the user has the rights, return true!
       // The reasoning is, that empty user-rights are the biggest rights, because the user is not limited!
       // In other words, the check-map holds the limits, so if checks are defined, the need to be done.
       // Each defined check in the CheckMap must match (AND), otherwise the user does not have the right for the resource.
    }
}

@JsExport
class UserRightsAction : P_List<UserRights>(UserRights::class) {
    fun matches(accessRightsAction: AccessRightsAction<*,*>): Boolean {
        TODO("Compare each resource attributes of the given action against all user rights of this action")
        // The actions will be the same, for example "readFeatures"
        // Each resource attributes must match at least one user-rights!
        // Only if every resource matches at least one user-rights the total action matches!
    }
}

@JsExport
class UserRightsService : P_Map<String, UserRightsAction>(String::class, UserRightsAction::class) {
    fun matches(accessRightsService: AccessRightsService): Boolean {
        TODO("Compare each access rights action of the given service against the corresponding user rights action of this service")
    }
}

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

    fun matches(accessRightsMatrix: AccessRightsMatrix<*>): Boolean {
        TODO("Compare each access rights service from the given matrix against the corresponding user rights service from this matrix")
    }

    fun compile(): UserRightsMatrix {
        TODO("Compile (convert) all shortcuts into full qualified CheckMaps")
        val x: UserRights
        PlatformMapApi.map_key_iterator(x.data())

        var raw = PlatformMapApi.map_get(x.data(), "foo")
        // For example, when the UserRights value is not a Map, but a string, then convert:
        if (raw is String)
        //   "value" -> EqualsCheck("value")
        //   "value*" -> StartsWithCheck("value")
        //   "*value" -> EndsWith("value")
        if (raw is PlatformList)
        //   ["a","b"] -> EqualsChecks("a", "b")
        // Do we need more?
        if (raw !is PlatformMap)
    }
}