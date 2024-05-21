package com.here.naksha.lib.auth.attribute

import com.here.naksha.lib.auth.toBaseArray

class SpaceAttributes(vararg args: Any) : CommonAttributes<StorageAttributes>(*args) {

    fun eventHandlerIds(eventHandlerIds: List<String>) =
        apply { set(EVENT_HANDLER_IDS_KEY, eventHandlerIds.toBaseArray()) }

    companion object {
        const val EVENT_HANDLER_IDS_KEY = "eventHandlerIds"
    }
}