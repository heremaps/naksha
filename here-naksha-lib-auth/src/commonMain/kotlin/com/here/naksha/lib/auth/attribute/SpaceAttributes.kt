package com.here.naksha.lib.auth.attribute

import com.here.naksha.lib.base.P_List

class SpaceAttributes : NakshaAttributes<StorageAttributes>() {

    fun eventHandlerIds(eventHandlerIds: List<String>) =
        apply {
            box(eventHandlerIds, P_List::class)?.let { set(EVENT_HANDLER_IDS_KEY, it) }
        }

    companion object {
        const val EVENT_HANDLER_IDS_KEY = "eventHandlerIds"
    }
}