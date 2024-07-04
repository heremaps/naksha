@file:Suppress("OPT_IN_USAGE")

package naksha.auth.action

import naksha.auth.attribute.StorageAttributes
import kotlin.js.JsExport

@JsExport
class UseStorages : AccessRightsAction<StorageAttributes, UseStorages>() {

    override val name: String = NAME

    companion object {
        const val NAME = "useStorages"
    }
}

@JsExport
class ManageStorages : AccessRightsAction<StorageAttributes, ManageStorages>() {

    override val name: String = NAME

    companion object {
        const val NAME = "manageStorages"
    }
}
