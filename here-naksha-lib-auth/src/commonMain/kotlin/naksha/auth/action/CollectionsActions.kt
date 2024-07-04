@file:Suppress("OPT_IN_USAGE")

package naksha.auth.action

import naksha.auth.attribute.CollectionAttributes
import kotlin.js.JsExport

@JsExport
class ReadCollections : AccessRightsAction<CollectionAttributes, ReadCollections>() {

    override val name: String = NAME

    companion object {
        const val NAME = "readCollections"
    }
}

@JsExport
class CreateCollections : AccessRightsAction<CollectionAttributes, CreateCollections>() {

    override val name: String = NAME

    companion object {
        const val NAME = "createCollections"
    }
}

@JsExport
class UpdateCollections : AccessRightsAction<CollectionAttributes, UpdateCollections>() {

    override val name: String = NAME

    companion object {
        const val NAME = "updateCollections"
    }
}

@JsExport
class DeleteCollections : AccessRightsAction<CollectionAttributes, DeleteCollections>() {

    override val name: String = NAME

    companion object {
        const val NAME = "deleteCollections"
    }
}
