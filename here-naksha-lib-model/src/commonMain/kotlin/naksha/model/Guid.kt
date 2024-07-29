@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The Global Unique Identifier uniquely identifies a feature, world-wide. When [toString] is invoked, it is serialized into a [URN](https://datatracker.ietf.org/doc/html/rfc8141). It can be restored from a [URN](https://datatracker.ietf.org/doc/html/rfc8141). The format of the URN is:
 *
 * `urn:here:naksha:guid:{storage-id}:{map}:{collection-id}:{feature-id}:{year}:{month}:{day}:{seq}:{uid}`
 * @since 3.0.0
 */
@JsExport
data class Guid(
    /**
     * The identifier of the storage in which the object is stored.
     */
    @JvmField
    val storageId: String,

    /**
     * The map in which the object is stored, with an empty string representing the default map of the storage.
     */
    @JvmField
    val map: String,

    /**
     * The identifier of the collection in which the feature is stored.
     */
    @JvmField
    val collectionId: String,

    /**
     * The identifier of the feature.
     */
    @JvmField
    val featureId: String,

    /**
     * The local unique identifier of the state of the feature referred. This persists out of the `version` and the `uid`, which is the version local unique identifier.
     */
    @JvmField
    val luid: Luid
) {
    private lateinit var _string: String

    /**
     * Create a [Guid] from a [row-address][RowAddr].
     * @param storageId the storage-id.
     * @param map the map.
     * @param collectionId the collection-id.
     * @param featureId the feature-id.
     * @param addr the row-address.
     */
    @JsName("of")
    constructor(storageId: String, map: String, collectionId: String, featureId: String, addr: RowAddr) :
            this(storageId, map, collectionId, featureId, Luid(addr.txn, addr.uid))

    /**
     * Return the GUID in URN form.
     * @return the GUID in URN form.
     * @since 3.0.0
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "urn:here:naksha:guid:${storageId}:${map}:${collectionId}:${featureId}:$luid"
        return _string
    }

    companion object GuidCompanion {
        const val URN = 0
        const val HERE = 1
        const val NAKSHA = 2
        const val GUID = 3
        const val STORAGE_ID = 4
        const val MAP = 5
        const val COLLECTION_ID = 6
        const val FEATURE_ID = 7
        const val YEAR = 8
        const val MONTH = 9
        const val DAY = 10
        const val SEQ = 11
        const val UID = 12
        const val PARTS = 13

        @JsStatic
        @JvmStatic
        fun fromString(s: String): Guid {
            val v = s.split(':')
            if (v.size != PARTS
                || v[URN] != "urn"
                || v[HERE] != "here"
                || v[NAKSHA] != "naksha"
                || v[GUID] != "guid"
            ) throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid GUID: $s")
            return Guid(
                v[STORAGE_ID],
                v[MAP],
                v[COLLECTION_ID],
                v[FEATURE_ID],
                Luid(Version.of(v[YEAR].toInt(), v[MONTH].toInt(),v[DAY].toInt(), Int64(v[SEQ].toLong())), v[UID].toInt())
            )
        }
    }
}