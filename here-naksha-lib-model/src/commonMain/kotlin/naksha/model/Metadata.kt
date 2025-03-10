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
    override val flags: Flags = Flags().withAction(Action.CREATED),
    override val updatedAt: Int64 = Platform.currentMillis(),
    override val createdAt: Int64? = null,
    override val authorTs: Int64? = null,
    override val nextTupleNumber: TupleNumber? = null,
    override val prevTupleNumber: TupleNumber? = null,
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
    override val uid: Int
        get() = tupleNumber.uid
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
     * Extracts the action from [flags] and return the enumeration value.
     * @return the enumeration value of action, extracted from [flags].
     */
    fun action() : Action = flags.actionEnum()

    override fun hashCode(): Int = tupleNumber.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IMetadata) return false
        return guid == other.guid
    }
    override fun toString(): String = "$id:$tupleNumber"

    companion object Metadata_C {

        /**
         * Helper method to create the new metadata, when performing the given operation, with the given feature as outcome of the operation, in the given session.
         *
         * Actually this method should be used, when the new metadata need to be calculated.
         * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given arguments are not sufficient to generate the new metadata.
         * @param session the session for which to perform the operation.
         * @param feature the new _(modified)_ state of the feature, for which the metadata should be created.
         * @param tupleNumber the new [TupleNumber] that is generated for the new state.
         * @param operation the [operation][Operation] that is performed.
         * @param action the [action][Action] being performed, if not given, it is expected that the given [operation][Operation] has a [fixed action][Operation.action].
         * @return the new metadata that is correct for the new state, based upon the given data.
         * @since 3.0.0
         * @see [StorageTx]
         */
        @JvmStatic
        @JsStatic
        fun forOperation(
            session: IWriteSession,
            feature: NakshaFeature,
            tupleNumber: TupleNumber,
            operation: Operation,
            action: Action = operation.action ?:
                throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "There is no default action defined for operation $operation")
        ): Metadata {
            var flags = session.storage.getEncodingFlags(feature, null)
                .withOperation(operation)
                .withAction(action)
            val xyz = feature.properties.xyz
            val prevTupleNumber = xyz.pguid?.tupleNumber
            val updatedAt: Int64 = Platform.currentMillis()
            val createdAt: Int64? = xyz.createdAt
            val author: String?
            val authorTs: Int64? = if (xyz.author == null || xyz.author != session.options.author) {
                // authorTs is always the same as updatedAt, when the author is updated.
                // If the previous author was null, we simply always assume a change.
                author = session.options.author
                null
            } else {
                // If the author stays the same as before, we need to remember the previous timestamp!
                author = xyz.author
                xyz.authorTs
            }
            val baseTupleNumber = xyz.mguid?.tupleNumber
            return Metadata(
                tupleNumber = tupleNumber,
                flags = flags,
                nextTupleNumber = null,
                updatedAt = updatedAt,
                createdAt = createdAt,
                authorTs = authorTs,
                prevTupleNumber = prevTupleNumber,
                baseTupleNumber = baseTupleNumber,
                changeCount = xyz.changeCount + 1,
                hash = calculateHash(feature),
                hereTile = calculateHereTile(feature),
                id = feature.id,
                appId = session.options.appId,
                author = author,
                origin = null, // TODO: Fix this, we need to detect foreign features!
                target = null, // TODO: Fix this, we need to detect join operations!
            )
        }

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
                other.flags,
                other.updatedAt,
                other.createdAt,
                other.authorTs,
                other.nextTupleNumber,
                other.prevTupleNumber,
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
                nextTupleNumber = xyz.nguid?.tupleNumber,
                prevTupleNumber = xyz.pguid?.tupleNumber,
                baseTupleNumber = xyz.mguid?.tupleNumber,
                flags = xyz.flags ?: Flags(xyz.action.intValue),
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
            flags = 0,
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

