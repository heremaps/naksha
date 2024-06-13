@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.CollectionAttributes
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@JsExport
class ReadCollections : AccessRightsAction<CollectionAttributes, ReadCollections>() {

    override val name: String = NAME

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "readCollections"
    }
}

@JsExport
class CreateCollections : AccessRightsAction<CollectionAttributes, CreateCollections>() {

    override val name: String = NAME

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "createCollections"
    }
}

@JsExport
class UpdateCollections : AccessRightsAction<CollectionAttributes, UpdateCollections>() {

    override val name: String = NAME

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "updateCollections"
    }
}

@JsExport
class DeleteCollections : AccessRightsAction<CollectionAttributes, DeleteCollections>() {

    override val name: String = NAME

    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "deleteCollections"
    }
}
