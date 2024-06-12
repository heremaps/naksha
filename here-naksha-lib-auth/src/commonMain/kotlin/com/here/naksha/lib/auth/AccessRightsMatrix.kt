@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.AccessRightsAction
import com.here.naksha.lib.auth.attribute.ResourceAttributes
import naksha.base.P_List
import naksha.base.P_Map
import kotlin.js.JsExport

/**
 * The abstract base class for access right matrices. Please use a specific type that is suitable for your service or
 * create an own new one.
 */
@JsExport
class AccessRightsMatrix :
    P_Map<String, ServiceAccessRights>(String::class, ServiceAccessRights::class) {

    fun naksha(): ServiceAccessRights = getService(NAKSHA_SERVICE_NAME)

    fun withService(name: String, service: ServiceAccessRights): AccessRightsMatrix = apply {
        val existing = getAs(name, ServiceAccessRights::class)
        if (existing == null) {
            put(name, service)
        } else {
            existing.mergeActionsFrom(service)
        }
    }

    fun getService(name: String): ServiceAccessRights =
        getOrCreate(name, ServiceAccessRights::class)

    companion object {
        const val NAKSHA_SERVICE_NAME: String = "naksha"
    }
}

@JsExport
class ServiceAccessRights :
    P_Map<String, AccessRightsAction<*, *>>(String::class, AccessRightsAction::class) {

    fun <T : AccessRightsAction<*, T>> withAction(action: T): ServiceAccessRights = apply {
        put(action.name, action)
    }

    fun mergeActionsFrom(otherService: ServiceAccessRights): ServiceAccessRights = apply {
        otherService.filterValues { it != null }
            .forEach { (actionName, notNullAction) ->
                val existing = getAs(actionName, AccessRightsAction::class)
                if (existing == null) {
                    put(actionName, notNullAction)
                } else {
                    existing.withAttributesFromAction(notNullAction!!)
                }
            }
    }

    fun getActionAttributeMaps(actionName: String): P_List<ResourceAttributes>? =
        getAs(actionName, P_List::class) as? P_List<ResourceAttributes>
}
