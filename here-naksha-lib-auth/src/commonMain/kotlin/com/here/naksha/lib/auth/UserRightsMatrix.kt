package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.*
import com.here.naksha.lib.base.*
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class UserRightsMatrix(vararg args: Any?) : P_Object(*args) {

    fun getService(serviceName: String): UserServiceMatrix? =
        getOrNull(serviceName, UserServiceMatrix::class)

    fun withService(serviceName: String, serviceMatrix: UserServiceMatrix): UserRightsMatrix =
        apply { set(serviceName, serviceMatrix) }

    fun matches(accessRightsMatrix: AccessRightsMatrix): Boolean {
        return getServices()
            .map { (serviceName, userServiceRights) ->
                val accessServiceRights = accessRightsMatrix.getService(serviceName)
                serviceAccessAllowed(userServiceRights, accessServiceRights)
            }
            .all { serviceAccess -> serviceAccess }
    }

    private fun serviceAccessAllowed(
        userServiceRights: UserServiceMatrix?,
        accessServiceRights: AccessServiceMatrix?
    ): Boolean {
        if (userServiceRights == null || accessServiceRights == null) {
            return false
        }
        return userServiceRights.matches(accessServiceRights)
    }


    private fun getServices(): Map<String, UserServiceMatrix?> =
        mapValues { (_, serviceMatrix) -> box(serviceMatrix, UserServiceMatrix::class) }
}

class UserServiceMatrix(vararg args: Any?) : P_Object(*args) {

    fun getActionAttributeMaps(actionName: String): List<UserAttributeMap>? =
        getOrNull(actionName, P_List::class) as? List<UserAttributeMap>


    fun matches(accessServiceRights: AccessServiceMatrix): Boolean {
        return attributeMapsByAction()
            .map { (action, userAttributesList) ->
                val accessAttributesList = accessServiceRights.getActionAttributeMaps(action)
                actionAccessAllowed(userAttributesList, accessAttributesList)
            }
            .all { actionAccess -> actionAccess }
    }

    private fun attributeMapsByAction(): Map<String, P_List<UserAttributeMap>?> =
        mapValues { (_, actionAttributeMaps) -> box(actionAttributeMaps, P_List::class) as P_List<UserAttributeMap> }

    private fun actionAccessAllowed(
        userAttributesList: P_List<UserAttributeMap>?,
        accessAttributesList: P_List<AccessAttributeMap>?
    ): Boolean {
        if (userAttributesList == null || accessAttributesList == null) {
            return false
        }
        return userAttributesList.any { userAttributes ->
            accessAttributesList.any { accessAttributes ->
                if(userAttributes == null || accessAttributes == null){
                    false
                } else {
                    userAttributes.matches(accessAttributes)
                }
            }
        }
    }
}


class UserAttributeMap(vararg args: Any?) : P_Object(*args) {

    fun matches(accessAttributes: AccessAttributeMap): Boolean =
        MatcherCompiler.compile(this).matches(accessAttributes)
}
