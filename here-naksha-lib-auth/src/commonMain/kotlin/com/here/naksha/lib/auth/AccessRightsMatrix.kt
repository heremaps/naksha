@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.AccessRightsAction
import com.here.naksha.lib.auth.attribute.ResourceAttributes
import com.here.naksha.lib.auth.service.NakshaService
import naksha.base.P_List
import naksha.base.P_Map
import kotlin.js.JsExport

/**
 * The abstract base class for access right matrices. Please use a specific type that is suitable for your service or
 * create an own new one.
 */
@JsExport
open class AccessRightsMatrix :
    P_Map<String, AccessRightsService>(String::class, AccessRightsService::class) {

    fun naksha(): NakshaService = getOrCreate(NakshaService.NAME, NakshaService::class)

    fun withService(name: String, service: AccessRightsService): AccessRightsMatrix = apply {
        val existing = getOrNull(name, service::class)
        if (existing == null) {
            put(name, service)
        } else {
            existing.mergeActionsFrom(service)
        }
    }

    fun getService(name: String): AccessRightsService =
        getOrCreate(name, AccessRightsService::class)

//    fun withActionAttributeMaps(
//        actionName: String,
//        vararg attributes: ResourceAttributes
//    ): AccessServiceMatrix {
//        val currentAttributeMaps = getOrNull(actionName, P_List::class)
//        val newAttributeMaps =
//            if (currentAttributeMaps == null || currentAttributeMaps.isEmpty()) {
//                BaseArray(*attributes)
//            } else {
//                Array(currentAttributeMaps.size() + attributes.size) { ind ->
//                    if (ind < currentAttributeMaps.size()) {
//                        currentAttributeMaps[ind]
//                    } else {
//                        attributes[ind - currentAttributeMaps.size()]
//                    }
//                }.let { BaseArray(*it) }
//            }
//        set(actionName, newAttributeMaps)
//        return this
//    }
//
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

@JsExport
open class AccessRightsService :
    P_Map<String, AccessRightsAction<*, *>>(String::class, AccessRightsAction::class) {

    fun <T : AccessRightsAction<*, T>> withAction(action: T): AccessRightsService = apply {
        put(action.name, action)
    }

    fun mergeActionsFrom(otherService: AccessRightsService): AccessRightsService = apply {
        putAll(otherService)
    }

    fun getActionAttributeMaps(actionName: String): P_List<ResourceAttributes>? =
        getOrNull(actionName, P_List::class) as? P_List<ResourceAttributes>
}
