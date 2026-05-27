@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Fnv1a32
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.fn.Fn3
import naksha.geo.HereTile
import naksha.model.objects.NakshaFeature
import kotlin.concurrent.Volatile
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The immutable on-heap representation of the metadata of a [Tuple].
 *
 * This is mainly used by applications, therefore the default value of [tupleNumber] is [TupleNumber.HEAD].
 * @since 3.0.0
 */
@JsExport
data class Metadata(
    override val tupleNumber: TupleNumber = TupleNumber.HEAD,
    override val dataEncoding: DataEncoding = DataEncoding.DEFAULT,
    override val updatedAt: Int64 = Platform.currentMillis(),
    override val createdAt: Int64? = null,
    override val authorTs: Int64? = null,
    override val nextVersion: Int64? = null,
    override val baseTupleNumber: TupleNumber? = null,
    override val changeCount: Int = 1,
    override val hash: Int = 0,
    override val hereTile: Int = 0,
    override val id: String,
    override val appId: String = NakshaContext.appId(),
    override val author: String? = NakshaContext.author(),
    override val origin: String? = null,
    override val target: String? = null,
    override val ft: String? = null,
    override val cv0: Double? = null,
    override val cv1: Double? = null,
    override val cv2: Double? = null,
    override val cv3: Double? = null,
    override val cs0: String? = null,
    override val cs1: String? = null,
    override val cs2: String? = null,
    override val cs3: String? = null,
) : IMetadata {
    override val storageNumber: Int64
        get() = tupleNumber.storageNumber
    override val mapNumber: Int
        get() = tupleNumber.mapNumber
    override val collectionNumber: Int
        get() = tupleNumber.collectionNumber
    override val featureNumber: Int64
        get() = tupleNumber.featureNumber
    override val partitionNumber: Int
        get() = tupleNumber.partitionNumber
    override val version: Version
        get() = tupleNumber.version
    override val txn: Int64
        get() = version.txn

    /**
     * Tests if this describes a new state.
     * @return _true_ if this describes a new state, not yet persisted; _false_, if it describes an existing state.
     */
    fun isNew(): Boolean = tupleNumber == TupleNumber.HEAD

    @Volatile
    private var _guid: Guid? = null

    /**
     * Returns the [Guid].
     * @return the [Guid].
     */
    override val guid: Guid
        get() {
            var guid = _guid
            if (guid == null) {
                guid = Guid(id, tupleNumber)
                _guid = guid
            }
            return guid
        }

    @Volatile
    private var _originGuid: Guid? = null

    override val originGuid: Guid?
        get() {
            var guid = _originGuid
            if (guid == null) {
                val origin = this.origin ?: return null
                try {
                    guid = Guid.fromString(origin)
                } catch (e: Exception) {
                    return null
                }
                _originGuid = guid
            }
            return guid
        }

    @Volatile
    private var _targetGuid: Guid? = null

    override val targetGuid: Guid?
        get() {
            var guid = _targetGuid
            if (guid == null) {
                val target = this.target ?: return null
                try {
                    guid = Guid.fromString(target)
                } catch (e: Exception) {
                    return null
                }
                _targetGuid = guid
            }
            return guid
        }

    /**
     * Returns the action encoded in the lower two bits of [Version.txn].
     */
    fun action() : Action = Action.fromValue((version.txn.toInt()) and 3)

    override fun hashCode(): Int = tupleNumber.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IMetadata) return false
        return guid == other.guid
    }
    override fun toString(): String = "$id:$tupleNumber"

    companion object Metadata_C {

        /**

         * Import other metadata into the heap representation.
         * @param other the other metadata.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromOther(other: IMetadata): Metadata {
            if (other is Metadata) return other
            return Metadata(
                other.tupleNumber,
                other.dataEncoding,
                other.updatedAt,
                other.createdAt,
                other.authorTs,
                other.nextVersion,
                other.baseTupleNumber,
                other.changeCount,
                other.hash,
                other.hereTile,
                other.id,
                other.appId,
                other.author,
                other.origin,
                other.target,
                other.ft,
                other.cv0, other.cv1, other.cv2, other.cv3,
                other.cs0, other.cs1, other.cs2, other.cs3
            )
        }

        /**
         * Creates the [Metadata] from the given [XYZ namespace][XyzNs].
         *
         * If the given [XYZ namespace][XyzNs] is not from an existing, really stored feature, then the method returns _null_, what means, that the feature to which this [XYZ namespace][XyzNs] is attached is a client modified version.
         * @param featureId the **feature-id**.
         * @param featureType the **feature-type**.
         * @param xyz the [XYZ namespace][XyzNs].
         * @return the [Metadata] created from it.
         * @since 3.0.0
         * @see [XyzNs.fromMetadata]
         */
        @JvmStatic
        @JsStatic
        fun fromXyzNs(featureId: String, featureType: String, xyz: XyzNs): Metadata? {
            val guid = xyz.guid ?: return null
            return Metadata(
                tupleNumber = guid.tupleNumber,
                nextVersion = xyz.nguid?.tupleNumber?.version?.txn,
                baseTupleNumber = xyz.mguid?.tupleNumber,
                dataEncoding = xyz.dataEncoding ?: DataEncoding.DEFAULT,
                updatedAt = xyz.updatedAt,
                createdAt = if (xyz.updatedAt == xyz.createdAt) null else xyz.createdAt,
                authorTs = if (xyz.updatedAt == xyz.authorTs) null else xyz.authorTs,
                hash = xyz.hash ?: 0,
                changeCount = xyz.changeCount,
                hereTile = xyz.hereTile ?: 0, // TODO: Fix me, update!
                appId = xyz.appId,
                author = xyz.author,
                id = featureId,
                origin = xyz.origin,
                target = xyz.target,
                ft = featureType,
                cv0 = xyz.cv0, cv1 = xyz.cv1, cv2 = xyz.cv2, cv3 = xyz.cv3,
                cs0 = xyz.cs0, cs1 = xyz.cs1, cs2 = xyz.cs2, cs3 = xyz.cs3,
            )
        }

        /**
         * The undefined metadata singleton.
         * @since 3.0.
         */
        @JvmField
        @JsStatic
        val UNDEFINED = Metadata(
            tupleNumber = TupleNumber.HEAD,
            dataEncoding = DataEncoding.DEFAULT,
            updatedAt = Int64(0),
            hash = 0,
            changeCount = 0,
            hereTile = 0,
            id = "undefined",
            appId = "undefined",
            author = null,
        )

        /**
         * Calculates the feature hash to be stored in [Metadata].
         * @param feature the feature.
         * @param excludePaths an optional list of paths to exclude.
         * @param excludeFn an optional function to call for the [feature], current path, current value to decide if the value should be excluded from hashing.
         * @return the hash.
         */
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic
        @JsStatic
        fun calculateHash(
            feature: NakshaFeature,
             excludePaths: List<Array<String>>? = null,
            excludeFn: Fn3<Boolean, NakshaFeature, List<String>, Any?>? = null
        ): Int {
            // TODO: We need to calculate the hash above the feature itself.
            //  - Order keys first.
            //  - Exclude the given paths
            //  - Always exclude ["properties", "@ns:com:here:xyz"]
            //  - The purpose of the hash is to find similar entries
            //    - We only care about real data changes (not times, author, other metadata)
            return Fnv1a32.string(0, feature.id)
        }

        /**
         * Calculate the HERE tile-id to be stored in [Metadata].
         * @param feature the feature for which to calculate the HERE tile-id.
         * @return the HERE tile-id _(aka the int-key)_.
         */
        @JvmStatic
        @JsStatic
        fun calculateHereTile(feature: NakshaFeature): Int {
            val c = feature.referencePoint ?: feature.geometry?.calculateCentroid()
            return if (c != null) HereTile(c.latitude, c.longitude).intKey else Fnv1a32.string(0, feature.id)
        }
    }

    // TODO: toByteArray - we have a binary encoding already in PgTupleLoader, move here
    //       fromByteArray - we have a binary encoding already in PgTupleLoader, move here
    //       maybe it is better to realize this with a MetadataByteArray (basically extract code from PgTupleLoader)
}

