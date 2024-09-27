@file:Suppress("ArrayInDataClass")

package naksha.psql

import naksha.model.*
import naksha.model.FetchMode.FetchMode_C.FETCH_ALL
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.Tuple
import naksha.model.objects.NakshaFeature
import kotlin.jvm.JvmField

/**
 * An internal helper class to load tuples stepwise, if [tuple] is not _null_, then all other values are ignored and the set tuple is expected to be complete.
 */
internal data class PgTuple(
    /**
     * Reference to specific storage implementation that allows to decode tuple to feature.
     */
    @JvmField val storage: PgStorage,

    /**
     * The tuple-number, a unique identifier for the tuple.
     */
    @JvmField val tupleNumber: TupleNumber,

    /**
     * If the tuple is already available.
     */
    @JvmField var tuple: Tuple? = null,

    /**
     * The bits about which parts of the tuple have been fetched.
     */
    @JvmField var fetchBits: FetchBits = FetchMode.FETCH_ID,

    /**
     * The metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Tuple] into a [NakshaFeature].
     */
    @JvmField var meta: Metadata? = null,

    /**
     * Feature encoded with [FeatureEncoding] algorithm described by [Metadata.flags].
     */
    @JvmField var feature: ByteArray? = null,

    /**
     * Geometry encoded with [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a geometry.
     */
    @JvmField var geo: ByteArray? = null,

    /**
     * Geometry-Reference-Point, encoded with the [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a reference point.
     */
    @JvmField var referencePoint: ByteArray? = null,

    /**
     * Tags encoded with [TagsEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have any tags.
     */
    @JvmField var tags: ByteArray? = null,

    /**
     * An arbitrary binary attachment.
     */
    @JvmField var attachment: ByteArray? = null
) : ITuple {

    override fun toTuple(): Tuple? {
        var tuple = this.tuple
        if (tuple != null) return tuple
        val meta = this.meta ?: return null
        if (fetchBits != FETCH_ALL) return null
        tuple = Tuple(storage, tupleNumber, fetchBits, meta, feature = this.feature, geo = this.geo, referencePoint = this.referencePoint, tags = this.tags)
        this.tuple = tuple
        return tuple
    }
}