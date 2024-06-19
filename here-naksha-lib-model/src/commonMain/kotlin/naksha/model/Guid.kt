package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


/**
 * The Global Unique Identifier uniquely identifies a feature, world wide. When toString is invoked, it is serialized into a URN. It can be restored from a URN. The format is:
 *
 * urn:here:naksha:guid:{storage-id}:{collection-id}:{feature-id}:{year}:{month}:{day}:{seq}:{uid}
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class Guid (
    val storageId: String,
    val collectionId: String,
    val featureId: String,
    val luid: Luid
) {
    private lateinit var _string: String

    /**
     * Return the GUID in URN form.
     * @return the GUID in URN form.
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "$storageId:$collectionId:$featureId:$luid"
        return _string
    }
}