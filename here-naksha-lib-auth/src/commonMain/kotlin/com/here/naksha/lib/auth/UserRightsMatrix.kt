package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.AccessAttributes
import com.here.naksha.lib.auth.AccessRightsMatrix
import com.here.naksha.lib.auth.AccessServiceRights
import com.here.naksha.lib.auth.toObjectList
import com.here.naksha.lib.base.*
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class UserRightsMatrix(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<UserRightsMatrix>() {
            override fun isInstance(o: Any?): Boolean = o is UserRightsMatrix

            override fun newInstance(vararg args: Any?): UserRightsMatrix =
                UserRightsMatrix()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun matches(accessRightsMatrix: AccessRightsMatrix): Boolean {
        return getServices()
            .map { (serviceName, userServiceRights) ->
                val accessServiceRights = accessRightsMatrix.getAccessMatrixForService(serviceName)
                serviceAccessAllowed(userServiceRights, accessServiceRights)
            }
            .all { serviceAccess -> serviceAccess }
    }

    private fun serviceAccessAllowed(
        userServiceRights: UserServiceRights?,
        accessServiceRights: AccessServiceRights?
    ): Boolean {
        if (userServiceRights == null || accessServiceRights == null) {
            return false
        }
        return userServiceRights.matches(accessServiceRights)
    }

    fun getAccessMatrixForService(serviceName: String): UserServiceRights? =
        getOrNull(serviceName, UserServiceRights.klass)

    private fun getServices(): Map<String, UserServiceRights?> {
        return data().iterator()
            .asSequence()
            .associate { (serviceName, rawServiceData) -> serviceName to convertServiceData(rawServiceData) }
    }

    private fun convertServiceData(raw: Any?): UserServiceRights? =
        raw?.let { Base.assign(it, UserServiceRights.klass) }
}

class UserServiceRights(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<UserServiceRights>() {
            override fun isInstance(o: Any?): Boolean = o is UserServiceRights

            override fun newInstance(vararg args: Any?): UserServiceRights =
                UserServiceRights()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun matches(accessServiceRights: AccessServiceRights): Boolean {
        return attributeMapsByAction()
            .map { (action, userAttributesList) ->
                val accessAttributesList = accessServiceRights.getAttributesForAction(action)
                actionAccessAllowed(userAttributesList, accessAttributesList)
            }
            .all { actionAccess -> actionAccess }
    }

    private fun actionAccessAllowed(
        userAttributesList: List<UserAttributes>?,
        accessAttributesList: List<AccessAttributes>?
    ): Boolean {
        if (userAttributesList == null || accessAttributesList == null) {
            return false
        }
        return userAttributesList.any { userAttributes ->
            accessAttributesList.any { accessAttributes ->
                userAttributes.matches(accessAttributes)
            }
        }
    }

    fun getAttributesForAction(actionName: String): List<UserAttributes>? =
        getOrNull(actionName, BaseList.klass)?.toObjectList(UserAttributes.klass)

    private fun attributeMapsByAction(): Map<String, List<UserAttributes>?> {
        return data().iterator()
            .asSequence()
            .associate { (actionName, rawAttributeMaps) ->
                actionName to convertUserAttributesList(rawAttributeMaps)
            }
    }

    private fun convertUserAttributesList(rawList: Any?): List<UserAttributes>? {
        return rawList
            ?.let { Base.assign(it, BaseList.klass) }
            ?.toObjectList(UserAttributes.klass)
    }
}


class UserAttributes(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<UserAttributes>() {
            override fun isInstance(o: Any?): Boolean = o is UserAttributes

            override fun newInstance(vararg args: Any?): UserAttributes =
                UserAttributes()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun matches(accessAttributes: AccessAttributes): Boolean =
        MatcherCompiler.compile(this).matches(accessAttributes)
}
