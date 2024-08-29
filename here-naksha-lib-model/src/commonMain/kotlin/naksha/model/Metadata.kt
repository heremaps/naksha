@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Fnv1a32
import naksha.base.Int64
import naksha.base.fn.Fn3
import naksha.geo.HereTile
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The on heap representation of the metadata of a [Tuple], generated by the storage, read-only for the client.
 *
 * Every row in the storage does have a unique [row identifier][TupleNumber], that is made out of [version], [uid], and the partition number read form [flags].
 */
@JsExport
data class Metadata(
    override val storeNumber: StoreNumber,
    override val updatedAt: Int64?,
    override val createdAt: Int64? = updatedAt,
    override val authorTs: Int64? = updatedAt,
    override var nextVersion: Version? = null,
    override val version: Version,
    override val prevVersion: Version? = null,
    override val uid: Int,
    override val puid: Int? = null,
    override val hash: Int = 0,
    override val changeCount: Int = 1,
    override val geoGrid: Int = 0,
    override val flags: Flags,
    override val id: String,
    override val appId: String,
    override val author: String?,
    override val type: String?,
    override val origin: String? = null
) : IMetadata {
    private var tupleNumber: TupleNumber? = null

    /**
     * Return the row identifier.
     * @return the row identifier.
     */
    fun rowId(): TupleNumber {
        var i = tupleNumber
        if (i == null) {
            i = TupleNumber(storeNumber, version, uid)
            tupleNumber = i
        }
        return i
    }

    /**
     * Extracts the action from [flags] and return the enumeration value.
     * @return the enumeration value of action, extracted from [flags].
     */
    fun action() : Action = when (flags.action()) {
        ActionValues.CREATED -> Action.CREATED
        ActionValues.UPDATED -> Action.UPDATED
        ActionValues.DELETED -> Action.DELETED
        else -> Action.UNKNOWN
    }

    override fun hashCode(): Int = rowId().hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Metadata) return false
        // Note: No, we did not forget "nextVersion" in this compare !!!
        //       Even when next-version is set eventually, the metadata should be treated as the same!
        return createdAt == other.createdAt
                && updatedAt == other.updatedAt
                && authorTs == other.authorTs
                && version == other.version
                && prevVersion == other.prevVersion
                && uid == other.uid
                && puid == other.puid
                && hash == other.hash
                && changeCount == other.changeCount
                && geoGrid == other.geoGrid
                && flags == other.flags
                && id == other.id
                && appId == other.appId
                && author == other.author
                && type == other.type
                && origin == other.origin
    }
    override fun toString(): String = "$id:$tupleNumber"

    companion object Metadata_C {
        /**
         * Calculates the feature hash to be stored in [Metadata].
         * @param feature the feature.
         * @param excludePaths an optional list of paths to exclude.
         * @param excludeFn an optional function to call for the [feature], current path, current value to decide if the value should be excluded from hashing.
         * @return the hash.
         */
        @JvmStatic
        @JsStatic
        fun hash(
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
         * Calculate the geo-grid value for [Metadata].
         * @param feature the feature for which to calculate the geo-grid.
         * @return the geo-grid value.
         */
        @JvmStatic
        @JsStatic
        fun geoGrid(feature: NakshaFeature): Int {
            val c = feature.referencePoint ?: feature.geometry?.calculateCentroid()
            return if (c != null) HereTile(c.latitude, c.longitude).intKey else Fnv1a32.string(0, feature.id)
        }
    }
}