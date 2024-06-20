@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.EventHandlerAttributes
import kotlin.js.JsExport

@JsExport
class UseEventHandlers : AccessRightsAction<EventHandlerAttributes, UseEventHandlers>() {

    override val name: String = NAME

    companion object {
        const val NAME = "useEventHandlers"
    }
}

@JsExport
class ManageEventHandlers : AccessRightsAction<EventHandlerAttributes, ManageEventHandlers>() {

    override val name: String = NAME

    companion object {
        const val NAME = "managerEventHandlers"
    }
}
