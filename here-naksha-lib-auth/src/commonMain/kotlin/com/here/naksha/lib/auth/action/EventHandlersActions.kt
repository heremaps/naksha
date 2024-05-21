package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.EventHandlerAttributes

class UseEventHandlers: AccessAction<EventHandlerAttributes>(USE_EVENT_HANDLERS_ACTION_NAME) {

    companion object {
        const val USE_EVENT_HANDLERS_ACTION_NAME = "useEventHandlers"
    }
}

class ManageEventHandlers: AccessAction<EventHandlerAttributes>(MANAGE_EVENT_HANDLERS_ACTION_NAME) {

    companion object {
        const val MANAGE_EVENT_HANDLERS_ACTION_NAME = "managerEventHandlers"
    }
}
