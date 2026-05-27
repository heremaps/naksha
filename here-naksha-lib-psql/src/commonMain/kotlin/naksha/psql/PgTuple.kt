@file:Suppress("ArrayInDataClass")

package naksha.psql

import naksha.base.Binary
import naksha.model.*
import naksha.model.Tuple
import naksha.model.objects.NakshaFeature
import kotlin.jvm.JvmField

/**
 * An internal helper class to load tuples stepwise.
 *
 * The [PgTuple] is created when receiving the load instruction, it will only have [index], [storage], and [tupleNumber] set initially.
 *
 * The [tuple] is fetched from cache, before actually creating the database query, except the [fetchMode] has the [NO_CACHE_BIT] bit set. If the [Tuple] is already matching all requires fields from [fetchMode], then nothing will happen. Otherwise, data is loaded into [meta], [geo], [referencePoint], [feature], [tags], and [attachment]. Eventually, the [toTuple] method is invoked, which will build the final tuple.
 */
internal data class PgTuple(
    /**
     * The index in the result-set.
     */
    @JvmField val index: Int,

    /**
     * Reference to specific storage implementation that allows to decode tuple to feature.
     */
    @JvmField val storage: PgStorage,

    /**
     * The tuple-number, a unique identifier for the tuple.
     */
    @JvmField val tupleNumber: TupleNumber,

    /**
     * The bits about which parts of the tuple are requested by the client.
     */
    @JvmField var fetchMode: FetchMode,

    /**
     * If the tuple is already available in the cache.
     */
    @JvmField var tuple: Tuple? = null,

    /**
     * The metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Tuple] into a [NakshaFeature].
     */
    @JvmField var meta: Metadata? = null,

    /**
     * Geometry encoded as raw `TWKB`.
     * Might be _null_, when the feature does not have a geometry.
     */
    @JvmField var geo: ByteArray? = null,

    /**
     * Geometry-Reference-Point, encoded as raw `TWKB`.
     * Might be _null_, when the feature does not have a reference point.
     */
    @JvmField var referencePoint: ByteArray? = null,

    /**
     * Feature encoded with [FeatureEncoding] algorithm described by [Metadata.flags].
     */
    @JvmField var feature: ByteArray? = null,

    /**
     * Tags encoded as `JBON_GZIP`.
     * Might be _null_, when the feature does not have any tags.
     */
    @JvmField var tags: ByteArray? = null,

    /**
     * An arbitrary binary attachment.
     */
    @JvmField var attachment: ByteArray? = null
) : ITuple {

    private var finalTuple: Tuple? = null

    override fun toTuple(): Tuple? {
        if (finalTuple != null) return finalTuple
        val meta = this.meta ?: return null
        var fetchMode = FetchMode(META_BIT)
        if (feature !== Binary.UNDEFINED) fetchMode = fetchMode.withFeature()
        if (geo !== Binary.UNDEFINED && referencePoint !== Binary.UNDEFINED) fetchMode = fetchMode.withGeometry()
        finalTuple = Tuple(
            meta,
            feature = this.feature,
            geo = this.geo,
            referencePoint = this.referencePoint,
            tags = this.tags,
            attachment = this.attachment,
            complete = true
        )//.merge(NakshaCache[meta.tupleNumber()])
        this.tuple = tuple
        return tuple
    }
}