@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.auth.action.ACTIONS_BY_NAME
import naksha.auth.action.AccessRightsAction
import naksha.auth.attribute.ResourceAttributes
import naksha.base.ListProxy
import naksha.base.MapProxy
import kotlin.js.JsExport

/**
 * The ARM ([AccessRightsMatrix]) describes what attributes are required for given Action to be performed in given Service.
 * It is main domain class of lib-auth module, besides the [UserRightsMatrix].
 *
 * It is meant to be constructed by the client who is bound to given Service so it can evaluate whether the access should be granted
 * for given incoming user request bearing [UserRightsMatrix] - see its documentation for details.
 */
@JsExport
class AccessRightsMatrix : MapProxy<String, ServiceAccessRights>(String::class, ServiceAccessRights::class) {

    fun useNaksha(): ServiceAccessRights = useService(NAKSHA_SERVICE_NAME)

    fun withService(name: String, service: ServiceAccessRights): AccessRightsMatrix = apply {
        val existing = getAs(name, ServiceAccessRights::class)
        if (existing == null) {
            put(name, service)
        } else {
            existing.mergeActionsFrom(service)
        }
    }

    fun useService(name: String): ServiceAccessRights
        = getOrCreate<ServiceAccessRights, String, AccessRightsMatrix>(name, ServiceAccessRights::class)

    companion object {
        const val NAKSHA_SERVICE_NAME: String = "naksha"
    }
}

@JsExport
class ServiceAccessRights : MapProxy<String, AccessRightsAction<*, *>>(String::class, AccessRightsAction::class) {

    override fun toValue(
        key: String,
        value: Any?,
        alt: AccessRightsAction<*, *>?
    ): AccessRightsAction<*, *>? {
        val actionKlass = ACTIONS_BY_NAME[key]
        if (actionKlass != null) return box(value, actionKlass, alt)
        return super.toValue(key, value, alt)
    }

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

    fun getResourceAttributesForAction(actionName: String): ResourceAttributesList? =
        getAs(actionName, ResourceAttributesList::class)
}

@JsExport
class ResourceAttributesList : ListProxy<ResourceAttributes>(ResourceAttributes::class)