package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.ResourceAttributes
import naksha.base.P_List
import kotlin.js.JsExport

/**
 * An action persists out of a list of [ResourceAttributes]'s.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE", "UNCHECKED_CAST")
@JsExport
sealed class AccessRightsAction<T : ResourceAttributes, SELF : AccessRightsAction<T, SELF>> :
    P_List<ResourceAttributes>(ResourceAttributes::class) {

   open val name: String = "unknownAction"

    /**
     * Add the given attribute maps.
     */
    fun withAttributes(vararg attributeMaps: T): SELF {
        addAll(attributeMaps)
        return this as SELF
    }

    fun withAttributesFromAction(otherAction: AccessRightsAction<*, *>): SELF {
        val typedValues = otherAction.map { box(it, elementKlass) }
        addAll(typedValues)
        return this as SELF
    }
}