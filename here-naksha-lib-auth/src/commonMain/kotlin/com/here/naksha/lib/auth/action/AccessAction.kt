package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.AccessAttributeMap
import kotlin.js.JsExport

@JsExport
abstract class AccessAction<T : AccessAttributeMap>(val name: String) {

    private val attributes: MutableList<T> = mutableListOf()

    fun withAttributes(vararg attributes: T): AccessAction<T> {
        this.attributes.addAll(attributes)
        return this
    }

    fun getAttributes(): Array<AccessAttributeMap> =
        (attributes as List<AccessAttributeMap>).toTypedArray()
}
