@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.SpaceAttributes
import kotlin.js.JsExport

@JsExport
class UseSpaces : AccessRightsAction<SpaceAttributes, UseSpaces>() {

    override val name: String = NAME

    companion object {
        const val NAME = "useSpaces"
    }
}

@JsExport
class ManageSpaces : AccessRightsAction<SpaceAttributes, ManageSpaces>() {

    override val name: String = NAME

    companion object {
        const val NAME = "manageSpaces"
    }
}
