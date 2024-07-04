package naksha.auth.action

import naksha.auth.attribute.ResourceAttributes
import naksha.base.AbstractListProxy
import kotlin.js.JsExport

/**
 * An action persists out of a list of [ResourceAttributes]'s.
 */
@Suppress("OPT_IN_USAGE", "UNCHECKED_CAST")
@JsExport
sealed class AccessRightsAction<T : ResourceAttributes, SELF : AccessRightsAction<T, SELF>> :
    AbstractListProxy<ResourceAttributes>(ResourceAttributes::class) {

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