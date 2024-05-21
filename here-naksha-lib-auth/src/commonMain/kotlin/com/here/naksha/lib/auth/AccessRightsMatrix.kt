package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.AccessAction
import com.here.naksha.lib.base.*
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
open class AccessRightsMatrix(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<AccessRightsMatrix>() {
            override fun isInstance(o: Any?): Boolean = o is AccessRightsMatrix

            override fun newInstance(vararg args: Any?): AccessRightsMatrix =
                AccessRightsMatrix()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    //TODO: fix bug in getOrCreate (N sequential invocations return different objects)
    fun getService(serviceName: String): AccessServiceMatrix =
        getOrCreate(serviceName, AccessServiceMatrix.klass)

    fun withService(serviceName: String, serviceMatrix: AccessServiceMatrix): AccessRightsMatrix {
        val existingService = getOrNull(serviceName, AccessServiceMatrix.klass)
        if (existingService == null) {
            set(serviceName, serviceMatrix)
        } else {
            existingService.mergeActionsFrom(serviceMatrix)
        }
        return this
    }
}

@JsExport
class AccessServiceMatrix(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<AccessServiceMatrix>() {
            override fun isInstance(o: Any?): Boolean = o is AccessServiceMatrix

            override fun newInstance(vararg args: Any?): AccessServiceMatrix =
                AccessServiceMatrix()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun withAction(action: AccessAction<*>): AccessServiceMatrix =
        withActionAttributeMaps(action.name, *action.getAttributes())

    fun getActionAttributeMaps(actionName: String): Array<AccessAttributeMap>? =
        getOrNull(actionName, BaseList.klass)?.toObjectArray(AccessAttributeMap.klass)

    fun mergeActionsFrom(otherService: AccessServiceMatrix): AccessServiceMatrix {
        otherService
            .getAttributeMapsByAction()
            .forEach { (action, attributeMaps) ->
                withActionAttributeMaps(action, *attributeMaps.toTypedArray())
            }
        return this
    }

    private fun getAttributeMapsByAction(): Map<String, List<AccessAttributeMap>> {
        return data().iterator()
            .asSequence()
            .filter { (_, rawAttributes) -> rawAttributes != null }
            .associate { (actionName, rawAttributes) ->
                actionName to convertAccessAttributesList(rawAttributes!!)
            }
    }

    private fun convertAccessAttributesList(rawList: Any): List<AccessAttributeMap> =
        Base.assign(rawList, BaseList.klass).toObjectList(AccessAttributeMap.klass)

    fun withActionAttributeMaps(
        actionName: String,
        vararg attributeMaps: AccessAttributeMap
    ): AccessServiceMatrix {
        val currentAttributeMaps = getOrNull(actionName, BaseList.klass)
        val newAttributeMaps =
            if (currentAttributeMaps == null || currentAttributeMaps.size() == 0) {
                BaseArray(*attributeMaps)
            } else {
                Array(currentAttributeMaps.size() + attributeMaps.size) { ind ->
                    if (ind < currentAttributeMaps.size()) {
                        currentAttributeMaps[ind]
                    } else {
                        attributeMaps[ind - currentAttributeMaps.size()]
                    }
                }.let { BaseArray(*it) }
            }
        set(actionName, newAttributeMaps)
        return this
    }
}

@JsExport
open class AccessAttributeMap(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<AccessAttributeMap>() {
            override fun isInstance(o: Any?): Boolean = o is AccessAttributeMap

            override fun newInstance(vararg args: Any?): AccessAttributeMap =
                AccessAttributeMap()
        }
    }

    override fun klass(): BaseKlass<*> = klass
}
