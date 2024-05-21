package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.*
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

    fun getService(serviceName: String): UserServiceMatrix? =
        getOrNull(serviceName, UserServiceMatrix.klass)

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


    private fun getServices(): Map<String, UserServiceMatrix?> {
        return data().iterator()
            .asSequence()
            .associate { (serviceName, rawServiceData) -> serviceName to convertServiceData(rawServiceData) }
    }

    private fun convertServiceData(raw: Any?): UserServiceMatrix? =
        raw?.let { Base.assign(it, UserServiceMatrix.klass) }
}

class UserServiceMatrix(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<UserServiceMatrix>() {
            override fun isInstance(o: Any?): Boolean = o is UserServiceMatrix

            override fun newInstance(vararg args: Any?): UserServiceMatrix =
                UserServiceMatrix()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun getActionAttributeMaps(actionName: String): List<UserAttributeMap>? =
        getOrNull(actionName, BaseList.klass)?.toObjectList(UserAttributeMap.klass)


    fun matches(accessServiceRights: AccessServiceMatrix): Boolean {
        return attributeMapsByAction()
            .map { (action, userAttributesList) ->
                val accessAttributesList = accessServiceRights.getActionAttributeMaps(action)
                actionAccessAllowed(userAttributesList, accessAttributesList)
            }
            .all { actionAccess -> actionAccess }
    }

    private fun attributeMapsByAction(): Map<String, Array<UserAttributeMap>?> {
        return data().iterator()
            .asSequence()
            .associate { (actionName, rawAttributeMaps) ->
                actionName to convertUserAttributesList(rawAttributeMaps)
            }
    }

    private fun actionAccessAllowed(
        userAttributesList: Array<UserAttributeMap>?,
        accessAttributesList: Array<AccessAttributeMap>?
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

    private fun convertUserAttributesList(rawList: Any?): Array<UserAttributeMap>? {
        return rawList
            ?.let { Base.assign(it, BaseList.klass) }
            ?.toObjectArray(UserAttributeMap.klass)
    }
}


class UserAttributeMap(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<UserAttributeMap>() {
            override fun isInstance(o: Any?): Boolean = o is UserAttributeMap

            override fun newInstance(vararg args: Any?): UserAttributeMap =
                UserAttributeMap()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun matches(accessAttributes: AccessAttributeMap): Boolean =
        MatcherCompiler.compile(this).matches(accessAttributes)
}
