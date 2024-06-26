package naksha.model

import naksha.base.Int64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic


/**
 * The Global Unique Identifier uniquely identifies a feature, world wide. When toString is invoked, it is serialized into a URN. It can be restored from a URN. The format is:
 *
 * urn:here:naksha:guid:{storage-id}:{collection-id}:{feature-id}:{year}:{month}:{day}:{seq}:{uid}
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class Guid(
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

    companion object {
        @JvmStatic
        fun fromString(s: String): Guid {
            val values = s.split(':')
            check(values.size == 8) { "invalid naksha uuid $s" }
            return Guid(
                values[0],
                values[1],
                values[2],
                Luid(
                    Txn.of(
                        values[3].toInt(),
                        values[4].toInt(),
                        values[5].toInt(),
                        Int64(values[6].toLong()),
                    ),
                    values[7].toInt(),
                )
            )
        }
    }
}