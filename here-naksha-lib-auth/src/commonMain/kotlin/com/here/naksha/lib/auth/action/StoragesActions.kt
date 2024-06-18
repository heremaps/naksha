@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.StorageAttributes
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
