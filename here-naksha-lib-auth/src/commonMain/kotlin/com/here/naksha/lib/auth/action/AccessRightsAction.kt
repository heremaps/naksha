package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.ResourceAttributes
import com.here.naksha.lib.base.P_List
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * An action persists out of a list of [ResourceAttributes]'s.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE", "UNCHECKED_CAST")
@JsExport
abstract class AccessRightsAction<T : ResourceAttributes, SELF: AccessRightsAction<T, SELF>>(elementKlass: KClass<T>) : P_List<T>(elementKlass) {
    /**
     * Add the given attribute maps.
     */
    fun add(vararg attributeMaps: T): SELF {
        for (attributeMap in attributeMaps) add(attributeMap)
        return this as SELF
    }
}