package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class Guid (
    val storageId: String,
    val collectionId: String,
    val featureId: String,
    val luid: Luid
) {
    private lateinit var _string: String

    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "$storageId:$collectionId:$featureId:$luid"
        return _string
    }
}