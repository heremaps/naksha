package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.AccessAction
import com.here.naksha.lib.base.N_Array
import com.here.naksha.lib.base.P_List
import com.here.naksha.lib.base.P_Object
import com.here.naksha.lib.base.set
import kotlin.js.JsExport

@JsExport
open class AccessRightsMatrix(vararg args: Any?) : P_Object(*args) {

    //TODO: fix bug in getOrCreate (N sequential invocations return different objects)
    fun getService(serviceName: String): AccessServiceMatrix =
        getOrCreate(serviceName, AccessServiceMatrix::class)

    fun withService(serviceName: String, serviceMatrix: AccessServiceMatrix): AccessRightsMatrix {
        val existingService = getOrNull(serviceName, AccessServiceMatrix::class)
        if (existingService == null) {
            set(serviceName, serviceMatrix)
        } else {
            existingService.mergeActionsFrom(serviceMatrix)
        }
        return this
    }
}

@JsExport
class AccessServiceMatrix(vararg args: Any?) : P_Object(*args) {

    fun withAction(action: AccessAction<*>): AccessServiceMatrix =
        withActionAttributeMaps(action.name, *action.getAttributes())

    fun getActionAttributeMaps(actionName: String): List<AccessAttributeMap>? =
        getOrNull(actionName, P_List::class) as List<AccessAttributeMap>

    fun mergeActionsFrom(otherService: AccessServiceMatrix): AccessServiceMatrix {
        otherService
            .forEach { (action, attributeMaps) ->
                withActionAttributeMaps(action, *attributeMaps as Array<out AccessAttributeMap>)
            }
        return this
    }

//    private fun getAttributeMapsByAction(): Map<String, List<AccessAttributeMap>> {
//        return data().iterator()
//            .asSequence()
//            .filter { (_, rawAttributes) -> rawAttributes != null }
//            .associate { (actionName, rawAttributes) ->
//                actionName to convertAccessAttributesList(rawAttributes!!)
//            }
//    }

//    private fun convertAccessAttributesList(rawList: Any): List<AccessAttributeMap> =
//        Base.assign(rawList, BaseList.klass).toObjectList(AccessAttributeMap.klass)

//    fun withActionAttributeMaps(
//        actionName: String,
//        vararg attributeMaps: AccessAttributeMap
//    ): AccessServiceMatrix {
//        val currentAttributeMaps = getOrNull(actionName, P_List::class)
//        val newAttributeMaps =
//            if (currentAttributeMaps == null || currentAttributeMaps.isEmpty()) {
//                BaseArray(*attributeMaps)
//            } else {
//                Array(currentAttributeMaps.size() + attributeMaps.size) { ind ->
//                    if (ind < currentAttributeMaps.size()) {
//                        currentAttributeMaps[ind]
//                    } else {
//                        attributeMaps[ind - currentAttributeMaps.size()]
//                    }
//                }.let { BaseArray(*it) }
//            }
//        set(actionName, newAttributeMaps)
//        return this
//    }

    fun withActionAttributeMaps(
        actionName: String,
        vararg attributeMaps: AccessAttributeMap
    ): AccessServiceMatrix {
        val currentAttributeMaps = getOrNull(actionName, N_Array::class)
        if (currentAttributeMaps == null || currentAttributeMaps.size() == 0) {
            set(actionName, attributeMaps)
        } else {
            val combinedAttributes =
                Array(currentAttributeMaps.size() + attributeMaps.size) { ind ->
                    if (ind < currentAttributeMaps.size()) {
                        currentAttributeMaps[ind]
                    } else {
                        attributeMaps[ind - currentAttributeMaps.size()]
                    }
                }
            set(actionName, combinedAttributes)
        }
        return this
    }
}

@JsExport
open class AccessAttributeMap(vararg args: Any?) : P_Object(*args) {
}
