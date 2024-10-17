@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model

import naksha.base.Int64
import naksha.model.request.query.TupleColumn.TupleColumn_C.VERSION
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The Global Unique Identifier of a feature.
 *
 * When [toString] is invoked, it is serialized into a [URN](https://datatracker.ietf.org/doc/html/rfc8141). It can be restored from a [URN](https://datatracker.ietf.org/doc/html/rfc8141) using the static helper [fromString]. The format of the URN is:
 *
 * `urn:here:naksha:guid:{feature-id}:{storage}:{map}:{collection}:{partition}:{year}:{month}:{day}:{seq}:{uid}:{flags}`
 * @since 3.0.0
 */
@JsExport
data class Guid(
    /**
     * The feature-id of the feature.
     * @since 3.0.0
     */
    @JvmField
    val featureId: String,

    /**
     * The tuple-number.
     * @since 3.0.0
     */
    @JvmField
    val tupleNumber: TupleNumber
) {
    private lateinit var _string: String

    /**
     * Return the GUID in URN form.
     * @return the GUID in URN form.
     * @since 3.0.0
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) {
             _string = "urn:here:naksha:guid:$featureId:$tupleNumber"
        }
        return _string
    }

    companion object GuidCompanion {
        const val URN = 0
        const val HERE = 1
        const val NAKSHA = 2
        const val GUID = 3
        const val FEATURE_ID = 4
        const val STORAGE_NUMBER = 5
        const val MAP_NUMBER = 6
        const val COLLECTION_NUMBER = 7
        const val PARTITION_NUMBER = 8
        const val YEAR = 9
        const val MONTH = 10
        const val DAY = 11
        const val SEQ = 12
        const val UID = 13
        const val FLAGS = 14
        const val PARTS = 15

        /**
         * Restore a [Guid] from the given URN (string).
         * @param urn the URN from which to deserialize the [Guid].
         * @return the deserialized [Guid].
         */
        @JsStatic
        @JvmStatic
        fun fromString(urn: String): Guid {
            val v = urn.split(':')
            if (v.size != PARTS
                || v[URN] != "urn"
                || v[HERE] != "here"
                || v[NAKSHA] != "naksha"
                || v[GUID] != "guid"
            ) throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid GUID: $urn")
            val featureId = v[FEATURE_ID]
            val storageNumber = Int64(v[STORAGE_NUMBER].toLong(10))
            val mapNumber = v[MAP_NUMBER].toInt(10)
            val colNumber = v[COLLECTION_NUMBER].toInt(10)
            val partNumber = v[PARTITION_NUMBER].toInt(10)
            val storeNumber = StoreNumber().mapNumber(mapNumber).collectionNumber(colNumber).partitionNumber(partNumber)
            val year = v[YEAR].toInt(10)
            val month = v[MONTH].toInt(10)
            val day = v[DAY].toInt(10)
            val seq = Int64(v[SEQ].toLong())
            val version = Version.of(year, month, day, seq)
            val uid = v[UID].toInt()
            val flags = v[FLAGS].toInt().storageNumber(false)
            val tupleNumber = TupleNumber(storageNumber, storeNumber, version, uid, flags)
            return Guid(featureId, tupleNumber)
        }
    }
}