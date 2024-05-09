package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class UserRightsMatrix(vararg args: Any?): BaseObject(args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<UserRightsMatrix>() {
            override fun isInstance(o: Any?): Boolean = o is AuthMatrix

            override fun newInstance(vararg args: Any?): UserRightsMatrix = UserRightsMatrix()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun getAccessMatrixForService(serviceName: String): UserAccessMatrix? {
        return get(serviceName) as UserAccessMatrix
    }
}

data class UserAccessMatrix(val attributesPerAction: Map<String, List<UserAttributeMap>>){

    fun getAttributesForAction(actionName: String): List<UserAttributeMap>? =
        attributesPerAction[actionName]
}

class UserAttributeMap: Map<String, Any> by HashMap()
