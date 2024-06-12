package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.ResourceAttributes
import naksha.base.P_List
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * An action persists out of a list of [ResourceAttributes]'s.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE", "UNCHECKED_CAST")
@JsExport
abstract class AccessRightsAction<T : ResourceAttributes, SELF : AccessRightsAction<T, SELF>>(
    elementKlass: KClass<T>
) : P_List<T>(elementKlass) {

    abstract val name: String

    /**
     * Add the given attribute maps.
     */
    fun withAttributes(vararg attributeMaps: T): SELF {
        addAll(attributeMaps)
        return this as SELF
    }
}