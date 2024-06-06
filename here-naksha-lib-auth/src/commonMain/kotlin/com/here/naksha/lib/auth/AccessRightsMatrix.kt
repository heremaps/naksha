@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.attribute.AccessRightsService
import com.here.naksha.lib.auth.service.NakshaService
import com.here.naksha.lib.base.P_Map
import kotlin.js.JsExport

/**
 * The abstract base class for access right matrices. Please use a specific type that is suitable for your service or
 * create an own new one.
 */
@JsExport
open class AccessRightsMatrix : P_Map<String, AccessRightsService>(String::class, AccessRightsService::class) {

    fun naksha(): NakshaService = getOrCreate(NakshaService.NAME, NakshaService::class)

    fun add(name: String, service: AccessRightsService): AccessRightsMatrix = apply {
        val existing = getOrNull(name, service::class)
        if (existing == null) {
            put(name, service)
        } else {
            // TODO: Merge existing with given service
            // existingService.mergeActionsFrom(serviceMatrix)
        }
    }


//    fun withAction(action: AccessAction<*>): AccessServiceMatrix = apply {
//        put(action.name, action)
//    }
//
//    fun getActionAttributeMaps(actionName: String): AccessAttributeMapList? =
//        getOrNull(actionName, AccessAttributeMapList::class)
//
//    fun mergeActionsFrom(otherService: AccessServiceMatrix): AccessServiceMatrix {
//        otherService
//            .forEach { (action, attributeMaps) ->
//                addActions(action, *attributeMaps as Array<out AccessAttributeMap>)
//            }
//        return this
//    }

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

//    fun addActions(vararg actions: AccessAction<*>): AccessServiceMatrix {
//        for (attributeMap in actions) {
//
//        }
//        val current = getActionAttributeMaps(actionName) ?: emptyList()
//        val finalAttributeMaps = current + actions
//        set(actionName, box(finalAttributeMaps, AccessAttributeMapList::class))
////        if (currentAttributeMaps.isNullOrEmpty()) {
////            set(actionName, attributeMaps)
////        } else {
////            val combinedAttributes =
////                Array(currentAttributeMaps.size + attributeMaps.size) { ind ->
////                    if (ind < currentAttributeMaps.size) {
////                        currentAttributeMaps[ind]
////                    } else {
////                        attributeMaps[ind - currentAttributeMaps.size]
////                    }
////                }
////            set(actionName, combinedAttributes)
////        }
//        return this
//    }
}