package com.here.naksha.lib.auth.attribute

import kotlin.js.JsExport

@JsExport
class StorageAttributes() : NakshaAttributes<StorageAttributes>() {

    fun className(className: String) = apply { set(CLASS_NAME_KEY, className) }

    fun spaceId(spaceId: String) = apply { set(SPACE_ID_KEY, spaceId) }

    companion object {
        const val CLASS_NAME_KEY = "className"
        const val SPACE_ID_KEY = "spaceId"
    }
}
