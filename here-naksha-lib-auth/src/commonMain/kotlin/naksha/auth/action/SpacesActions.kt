@file:Suppress("OPT_IN_USAGE")

package naksha.auth.action

import naksha.auth.attribute.SpaceAttributes
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
