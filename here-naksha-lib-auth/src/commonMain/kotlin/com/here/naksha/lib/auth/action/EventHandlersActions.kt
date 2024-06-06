@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.EventHandlerAttributes
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@JsExport
class UseEventHandlers: AccessRightsAction<EventHandlerAttributes, UseEventHandlers>(EventHandlerAttributes::class) {

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "useEventHandlers"
    }
}

@JsExport
class ManageEventHandlers: AccessRightsAction<EventHandlerAttributes, ManageEventHandlers>(EventHandlerAttributes::class) {

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "managerEventHandlers"
    }
}
