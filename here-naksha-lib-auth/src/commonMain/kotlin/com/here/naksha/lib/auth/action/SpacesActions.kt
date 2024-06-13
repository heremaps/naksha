@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.SpaceAttributes
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@JsExport
class UseSpaces: AccessRightsAction<SpaceAttributes, UseSpaces>() {

    override val name: String = NAME

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "useSpaces"
    }
}

@JsExport
class ManageSpaces: AccessRightsAction<SpaceAttributes, ManageSpaces>(){

    override val name: String = NAME

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "manageSpaces"
    }
}
