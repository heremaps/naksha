@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Fnv1a32
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.fn.Fn3
import naksha.geo.HereTile
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
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
    val tupleNumber: TupleNumber = TupleNumber.HEAD,
    val dataEncoding: DataEncoding = DataEncoding.DEFAULT,
    val updatedAt: Int64 = Platform.currentMillis(),
    val createdAt: Int64? = null,
    val authorTs: Int64? = null,
    val nextVersion: Int64? = null,
    val baseTupleNumber: TupleNumber? = null,
    val changeCount: Int = 1,
    val hash: Int = 0,
    val hereTile: Int = 0,
    val id: String,
    val appId: String = NakshaContext.appId(),
    val author: String? = NakshaContext.author(),
    val origin: String? = null,
    val target: String? = null,
    val ft: String? = null,
    val cv0: Double? = null,
    val cv1: Double? = null,
    val cv2: Double? = null,
    val cv3: Double? = null,
    val cs0: String? = null,
    val cs1: String? = null,
    val cs2: String? = null,
    val cs3: String? = null,
) {
    val storageNumber: Int64
        get() = tupleNumber.storageNumber
    val mapNumber: Int
        get() = tupleNumber.mapNumber
    val collectionNumber: Int
        get() = tupleNumber.collectionNumber
    val featureNumber: Int64
        get() = tupleNumber.featureNumber
    val partitionNumber: Int
        get() = tupleNumber.partitionNumber
    val version: Version
        get() = tupleNumber.version
    val txn: Int64
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
    val guid: Guid
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

    val originGuid: Guid?
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

    val targetGuid: Guid?
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
        if (other !is Metadata) return false
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
        fun fromOther(other: Any?): Metadata? {
            if (other is Metadata) return other
            if (other is Tuple) {
                return Metadata(
                    tupleNumber = other.tupleNumber,
                    dataEncoding = other.dataEncoding,
                    updatedAt = other.getLongMember(StandardMembers.XyzUpdatedAt),
                    createdAt = other.getLongMember(StandardMembers.CreatedAtXyz),
                    authorTs = other.getLongMember(StandardMembers.XyzAuthorTimestamp),
                    nextVersion = if (other.nextVersion == Int64(-1L)) null else other.nextVersion,
                    baseTupleNumber = null,
                    changeCount = other.getIntMember(StandardMembers.ChangeCountXyz),
                    hash = other.getIntMember(StandardMembers.Hash),
                    hereTile = other.getIntMember(StandardMembers.HereTileXyz),
                    id = other.getStringMember(StandardMembers.Id) ?: "undefined",
                    appId = other.getStringMember(StandardMembers.AppIdXyz) ?: NakshaContext.appId(),
                    author = other.getStringMember(StandardMembers.AuthorXyz),
                    origin = other.getStringMember(StandardMembers.OriginXyz),
                    target = other.getStringMember(StandardMembers.TargetXyz),
                    ft = other.getStringMember(StandardMembers.FeatureType),
                    cv0 = other.getDoubleMember(StandardMembers.CustomValue0Xyz),
                    cv1 = other.getDoubleMember(StandardMembers.CustomValue1Xyz),
                    cv2 = other.getDoubleMember(StandardMembers.XyzCustomValue2),
                    cv3 = other.getDoubleMember(StandardMembers.XyzCustomValue3),
                    cs0 = other.getStringMember(StandardMembers.XyzCustomString0),
                    cs1 = other.getStringMember(StandardMembers.XyzCustomString1),
                    cs2 = other.getStringMember(StandardMembers.CustomString2),
                    cs3 = other.getStringMember(StandardMembers.XyzCustomString3),
                )
            }
            return null
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

