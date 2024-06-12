@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.StorageAttributes
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@JsExport
class UseStorages : AccessRightsAction<StorageAttributes, UseStorages>(StorageAttributes::class) {

    override val name: String = NAME

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "useStorages"
    }
}

@JsExport
class ManageStorages :
    AccessRightsAction<StorageAttributes, ManageStorages>(StorageAttributes::class) {

    override val name: String = NAME

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "manageStorages"
    }
}
