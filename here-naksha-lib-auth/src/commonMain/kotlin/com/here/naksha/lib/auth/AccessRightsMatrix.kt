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
open class AccessRightsMatrix :
    P_Map<String, AccessRightsService>(String::class, AccessRightsService::class) {

    fun naksha(): AccessRightsService = getOrCreate("naksha", AccessRightsService::class)

    fun withService(name: String, service: AccessRightsService): AccessRightsMatrix = apply {
        val existing = getAs(name, AccessRightsService::class)
        if (existing == null) {
            put(name, service)
        } else {
            existing.mergeActionsFrom(service)
        }
    }

    fun getService(name: String): AccessRightsService =
        getOrCreate(name, AccessRightsService::class)
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
        getAs(actionName, P_List::class) as? P_List<ResourceAttributes>
}
