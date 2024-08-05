@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_byte_array
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int8
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A helper that wraps a byte-array that contains a metadata byte-array.
 * ```sql
 * SELECT r AS (
 *   SELECT row_number, ... FROM table1 WHERE ... LIMIT ...
 *   UNION ALL
 *   SELECT row_number, ... FROM table2 WHERE ... LIMIT ...
 *   ...
 * )
 * SELECT gzip(string_agg(row_number
 * ||int8send(updated_at)
 * ||int8send(coalesce(created_at, updated_at))
 * ||int8send(coalesce(author_ts, updated_at))
 * ||int8send(coalesce(txn_next, 0::bigint))
 * ||int8send(coalesce(ptxn, 0::bigint))
 * ||int4send(coalesce(puid, 0))
 * ||int4send(change_count)
 * ||int4send(hash)
 * ||int4send(geo_grid)
 * ||int4send(flags)
 * ||id::bytea||'\x00'::bytea
 * ||app_id::bytea||'\x00'::bytea
 * ||coalesce(author,'')::bytea||'\x00'::bytea
 * ||coalesce(type,'')::bytea||'\x00'::bytea
 * ||coalesce(origin,'')::bytea||'\x00'::bytea
 * )) FROM r
 * ```
 * There are two helpers
 * @property storage the storage from which the metadata is loaded.
 * @property binary the binary data.
 */
@JsExport
data class MetadataByteArray(@JvmField val storage: IStorage, @JvmField val binary: ByteArray) : IMetadataArray {
    private val view: PlatformDataView = Platform.newDataView(binary)
    private val offsets: IntArray
    private val last: Int
    private val metadata: Array<Metadata?>

    init {
        val SIZE = binary.size
        if (SIZE == 0) {
            this.offsets = IntArray(0)
            this.last = 0
        } else {
            // Worst case is, that every string is an empty string
            var indices = IntArray(((SIZE / END) + 1))
            val view = this.view
            var i = 0
            var startPos = 0
            var zeros = 0
            var pos = END    // skip over header to ID start
            while (pos < SIZE) {
                if (dataview_get_int8(view, pos) == ZERO && ++zeros == 5) {
                    // 0=start, 1=after id, 2=after appId, 3=after author, 4=after type, 5=after origin, before start
                    indices[i++] = startPos
                    startPos = pos + 1 // skip ASCII zero
                    zeros = 0
                    pos += END // skip over header to ID start
                    continue
                }
                pos++
            }
            indices[i++] = startPos
            indices = indices.copyOf(i)
            this.offsets = indices
            this.last = indices[indices.size - 1]
        }
        metadata = arrayOfNulls(offsets.size)
    }

    override operator fun get(index: Int): Metadata {
        val metadata = this.metadata
        if (index < 0 || index >= metadata.size) throw IndexOutOfBoundsException()
        var meta = metadata[index]
        if (meta == null) {
            val bytes = dataview_get_byte_array(view)
            val offset = offsets[index]

            var start = offset + END
            var end = start
            while (dataview_get_int8(view, end) != ZERO) end++
            val id = bytes.decodeToString(start, end)

            start = ++end
            while (dataview_get_int8(view, end) != ZERO) end++
            val appId = bytes.decodeToString(start, end)

            start = ++end
            while (dataview_get_int8(view, end) != ZERO) end++
            val author = if (start == end) null else bytes.decodeToString(start, end)

            start = ++end
            while (dataview_get_int8(view, end) != ZERO) end++
            val type = if (start == end) null else bytes.decodeToString(start, end)

            start = ++end
            while (dataview_get_int8(view, end) != ZERO) end++
            val origin = if (start == end) null else bytes.decodeToString(start, end)

            val updated_at = dataview_get_int64(view, offset + UPDATED_AT)
            val created_at = dataview_get_int64(view, offset + CREATED_AT)
            val author_ts = dataview_get_int64(view, offset + AUTHOR_TS)
            meta = Metadata(
                storeNumber = dataview_get_int64(view, offset + STORE_NUMBER),
                updatedAt = dataview_get_int64(view, offset + UPDATED_AT),
                createdAt = if (created_at != ZERO_INT64) created_at else updated_at,
                authorTs = if (author_ts != ZERO_INT64) author_ts else updated_at,
                nextVersion = dataview_get_int64(view, offset + TXN_NEXT),
                version = dataview_get_int64(view, offset + TXN),
                prevVersion = dataview_get_int64(view, offset + PTXN),
                uid = dataview_get_int32(view, offset + UID),
                puid = dataview_get_int32(view, offset + PUID),
                hash = dataview_get_int32(view, offset + HASH),
                changeCount = dataview_get_int32(view, offset + CHANGE_COUNT),
                geoGrid = dataview_get_int32(view, offset + GEO_GRID),
                flags = dataview_get_int32(view, offset + FLAGS),
                id = id,
                appId = appId,
                author = author,
                type = type,
                origin = origin
            )
            metadata[index] = meta
        }
        return meta
    }

    companion object MetadataByteArray_C {
        private val ZERO_INT64 = Int64(0)
        private const val ZERO = 0.toByte()

        private const val STORE_NUMBER = 0
        private const val TXN = 8
        private const val UID = 16
        private const val PUID = 20
        private const val UPDATED_AT = 24
        private const val CREATED_AT = 32
        private const val AUTHOR_TS = 40
        private const val TXN_NEXT = 48
        private const val PTXN = 56
        private const val CHANGE_COUNT = 64
        private const val HASH = 68
        private const val GEO_GRID = 72
        private const val FLAGS = 76
        private const val END = 80

        /**
         * Return a [MetadataByteArray] from a compressed byte-array.
         * @param storage the storage from which the rows are.
         * @param compressed the compressed metadat binary.
         * @return the [MetadataByteArray].
         */
        @JvmStatic
        @JsStatic
        fun fromGzip(storage: IStorage, compressed: ByteArray): MetadataByteArray =
            MetadataByteArray(storage, Platform.gzipInflate(compressed))
    }

    /**
     * Returns the amount of row-ids in the array.
     */
    override val size: Int
        get() = metadata.size

    /**
     * Returns the row-ids compressed (this is helpful for in-memory caching).
     * @return the compressed row-ids.
     */
    fun gzip(): ByteArray = Platform.gzipDeflate(binary)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MetadataByteArray
        return binary.contentEquals(other.binary)
    }

    override fun hashCode(): Int = binary.contentHashCode()
    override fun toString(): String = binary.contentToString()
}