@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.decodeURIComponent
import naksha.base.Platform.PlatformCompanion.encodeURIComponent
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The Global Unique Identifier of a feature.
 *
 * When [toString] is invoked, it is serialized into a [URN](https://datatracker.ietf.org/doc/html/rfc8141). It can be restored from a [URN](https://datatracker.ietf.org/doc/html/rfc8141) using the static helper [fromString]. The format of the URN is:
 *
 * `urn:naksha:guid:{feature-id}:{storage-number}:{map-number}:{collection-number}:{feature-number}:{year}:{month}:{day}:{seq}:{uid}`
 *
 * The [Guid] is exposed through the [XYZ namespace][XyzNs] in the [uuid][XyzNs.uuid] property.
 * @since 3.0.0
 */
@JsExport
data class Guid(
    /**
     * The feature-id of the feature.
     * @since 3.0.0
     */
    @JvmField
    val id: String,

    /**
     * The tuple-number.
     * @since 3.0.0
     */
    @JvmField
    val tupleNumber: TupleNumber
) {
    private lateinit var _string: String

    /**
     * Tests if this is a _HEAD_ [Guid].
     * @return _true_ if this is a _HEAD_ [Guid]; _false_ otherwise.
     * @since 3.0.0
     */
    fun isHead(): Boolean = tupleNumber == TupleNumber.HEAD

    /**
     * Return the GUID in URN form.
     * @return the GUID in URN form.
     * @since 3.0.0
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) {
             _string = if (tupleNumber != TupleNumber.HEAD) "urn:naksha:guid:${encodeURIComponent(id)}:$tupleNumber"
                                                            else "urn:naksha:guid:${encodeURIComponent(id)}"
        }
        return _string
    }

    companion object GuidCompanion {
        internal const val URN = 0
        internal const val NAKSHA = 1
        internal const val GUID = 2
        internal const val FEATURE_ID = 3
        internal const val ID_ONLY_PARTS = 4
        internal const val STORAGE_NUMBER = 4
        internal const val MAP_NUMBER = 5
        internal const val COLLECTION_NUMBER = 6
        internal const val FEATURE_NUMBER = 7
        internal const val YEAR = 8
        internal const val MONTH = 9
        internal const val DAY = 10
        internal const val SEQ = 11
        internal const val UID = 12
        internal const val ALL_PARTS = 13

        /**
         * Create a _HEAD_ [Guid] for the given feature-id.
         * @param id the feature-id.
         * @return the _HEAD_ [Guid].
         */
        @JsStatic
        @JvmStatic
        fun headOf(id: String) = Guid(id, TupleNumber.HEAD)

        /**
         * Create a _HEAD_ [Guid] for the given feature.
         * @param feature the feature.
         * @return the _HEAD_ [Guid].
         */
        @JsName("headOfFeature")
        @JsStatic
        @JvmStatic
        fun headOf(feature: NakshaFeature) = Guid(feature.id, TupleNumber.HEAD)

        /**
         * Restore a [Guid] from the given URN (string).
         * @param urn the URN from which to deserialize the [Guid].
         * @return the deserialized [Guid].
         */
        @JsStatic
        @JvmStatic
        fun fromString(urn: String): Guid {
            val v = urn.split(':')
            if ((v.size != ALL_PARTS && v.size != ID_ONLY_PARTS)
                || v[URN] != "urn"
                || v[NAKSHA] != "naksha"
                || v[GUID] != "guid"
            ) throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid GUID: $urn")
            val featureId = decodeURIComponent(v[FEATURE_ID])
            val tupleNumber: TupleNumber = if (v.size == ID_ONLY_PARTS) {
                TupleNumber.HEAD
            } else { // v.size == ALL_PARTS
                TupleNumber.fromParts(v, STORAGE_NUMBER)
            }
            return Guid(featureId, tupleNumber)
        }

        /**
         * Turn the given [IMetadata] into a [Guid].
         * @param metadata the [IMetadata] for which to generate a [Guid].
         * @return the created [Guid].
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun fromMetadata(metadata: IMetadata): Guid = metadata.guid
    }
}