@file:Suppress("OPT_IN_USAGE")

package naksha.psql.executors

import naksha.model.*
import naksha.model.FetchMode.FETCH_ALL
import naksha.model.FetchMode.FETCH_ID
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.request.*
import naksha.psql.PgSession
import naksha.psql.PgStorage
import kotlin.js.JsExport
import kotlin.math.min

/**
 * A result-set.
 */
@JsExport
class PgResultSet(
    /**
     * The storage that generated this result-set.
     */
    override val storage: PgStorage,

    /**
     * The session that produced this result-set.
     */
    override val session: PgSession,

    /**
     * The result-set as [naksha.model.TupleNumber] array, read from the query.
     *
     * **Note**: After sorting, the array is replaced with the ordered version. This is quite important to acknowledge when saving the rows-ids to restore result-sets quickly when seeking in them!
     */
    internal var tupleNumberArray: TupleNumberByteArray,

    /**
     * Signal that the [tupleNumberArray] is incomplete (not the full result set).
     *
     * - This requires that [offset] is 0.
     * - This requires that [limit] is `rowIdArray.size`.
     * - This requires that [orderBy] and [filters] are both _null_.
     *
     * We only produce incomplete result-sets, when the client does not want a `handle` and does not filter the results by properties or custom filter methods.
     */
    internal val incomplete: Boolean,

    /**
     * How many entries in the [tupleNumberArray] are already filtered.
     */
    internal var validTill: Int = 0,

    /**
     * The offset where in the ordered, and filtered, result-set ([tupleNumberArray]) to start reading.
     */
    override val offset: Int = 0,

    /**
     * The limit the client needs.
     *
     * - If the client set the _limit_ to _null_, then a default value is used (currently 10,000)
     * - If this is restored for a [ReadHandle][naksha.model.request.ReadHandle], then the limit will be restored from the handle, except the client changes the limit via request parameter.
     * - If this is the result of a [WriteRequest][naksha.model.request.WriteRequest], then the limit should be set to `rowIdArray.size`.
     */
    override val limit: Int = if (incomplete) tupleNumberArray.size else 10_000,

    /**
     * The order to ensure, if _null_ the current order of [tupleNumberArray] is good.
     *
     * - If restored from a [ReadHandle] request, and from a cached result-set, then this will be _null_.
     * - If a `handle` is requested by the client, and this is the result of a query, then this parameter should be set to [OrderBy.deterministic]. In no case _null_ is acceptable in this case, because without an order it is not possible to generate a handle. This means as well, the result-set must not be [incomplete].
     */
    internal val orderBy: OrderBy? = null,

    /**
     * Optional filters to run above the ordered results to remove or modify result-rows.
     *
     * - If restored from a [ReadHandle] request, and from a cached result-set, then this may be _null_, but could be as well set, when the given [tupleNumberArray] is not yet fully filtered.
     * - If the client requested a query above the `properties` using an [IPropertyQuery][naksha.model.request.query.IPropertyQuery], then a method need to be created, and added as first result-filter in this filter list.
     */
    internal val filters: ResultFilterList? = null
) : IResultSet {
    /**
     * The generated results rows, same size as [tupleNumberArray].
     */
    internal var all: ResultTupleList

    override var end: Int
        internal set

    override val hardCap: Int
        get() = storage.hardCap

    /**
     * The results between [offset] and [end].
     */
    private var _result: ResultTupleList? = null

    override val result: ResultTupleList
        get() {
            var result = _result
            if (result == null) {
                result = all.subList(offset, end).proxy(ResultTupleList::class)
                _result = result
            }
            return result
        }

    override val resultSize: Int
        get() = result.size

    companion object ResultSet_C {
        private val DETERMINISTIC = OrderBy.deterministic()
        private val VERSION = OrderBy.version()
        private val ID = OrderBy.id()
    }

    // version
    private fun order_txn_uid(a: ResultTuple?, b: ResultTuple?): Int {
        if (a === b) return 0
        if (a == null) return 1
        if (b == null) return -1

        val v = a.tupleNumber.version.txn - b.tupleNumber.version.txn
        if (v < 0) return -1
        if (v > 0) return 1
        val u = a.tupleNumber.uid - b.tupleNumber.uid
        if (u < 0) return -1
        if (u > 0) return 1
        return 0
    }

    private fun order_id_txn_uid(a: ResultTuple?, b: ResultTuple?): Int {
        if (a === b) return 0
        if (a == null) return 1
        if (b == null) return -1

        val afid = a.featureId
        val bfid = b.featureId
        if (afid != bfid) {
            if (afid == null) return 1
            if (bfid == null) return -1
            return afid.compareTo(bfid)
        }
        return order_txn_uid(a, b)
    }

    /* NOTES
    We could allow any sort order, but sort orders that needs the full feature, will cause problems with huge cardinality, except we have everything in the cache!

    Loading only metadata, with an expected maximum size of 512 byte, may require in the worst case a data transfer time for one million rows:
    - (1,000,000 x 512) =~ 490 MiB
    - 490 MiB / 5 Gbps =~ 760ms

    In these cases a pre-sorting in the database may be better, and then set oderBy to null.

    Ordering by ID should not be that critical, reading ID and rowId can be optimized heavily, for example we can fetch as:

    WITH r AS (SELECT rowid, id FROM table WHERE ... LIMIT n)
    SELECT gzip(string_agg(rowid||id::bytea, '\x00'::bytea)) AS d FROM r;

    We know that the "id" must not contain an ASCII zero, so we can decode the above like:
    - We unzip it
    - We iterate through bytes and extract txn (8 byte), uid (4 byte), flags (4 byte), then everything until we hit EOF or ASCII-0.

    This can be implemented as a simple class the same way current RowIdArray is, except that it should only have a toArray() method, returning a RowResultList.

    */

    /**
     * The fetch that needs to be done, _null_ means validation is instant.
     */
    private var fetchMode: FetchMode? = null

    init {
        all = ResultTupleList.fromTupleNumberArray(storage, tupleNumberArray)
        if (orderBy == DETERMINISTIC || orderBy == VERSION) {
            fetchMode = null
            all.sortWith(this::order_txn_uid)
        } else if (orderBy == ID) {
            // We need to sort by ID, so we need the ids of all results!
            fetchMode = FETCH_ID
            session.fetchTuples(all, mode = FETCH_ID)
            all.sortWith(this::order_id_txn_uid)
        } else if (orderBy != null) {
            // TODO: We may use Naksha.FETCH_META or FETCH_ALL to implement more advanced search methods!
            throw NakshaException(ILLEGAL_ARGUMENT, "Unsupported orderBy: $orderBy")
        }
        if (!filters.isNullOrEmpty()) {
            // Filters need the full feature, the good thing about the filters,
            // they are applied after sorting, therefore they do not need to load
            // all features, if there is a reasonable limit!
            fetchMode = FETCH_ALL
        }
        if (!incomplete) {
            end = offset + limit
            // If limit is Int.MAX_VALUE, we get an overflow below zero, but we mean: everything!
            if (end < 0 || end > tupleNumberArray.size) end = tupleNumberArray.size
            validateTill(end)
        } else {
            end = tupleNumberArray.size
            validTill = end
        }
    }

    override val tuples: ResultTupleList
        get() = all

    override val validationEnd: Int
        get() = validTill

    override fun validateTill(end: Int) {
        if (incomplete) throw NakshaException(ILLEGAL_STATE, "The result-set is incomplete, no further validation can be done")
        if (isComplete() || end <= validTill) return

        val fetchMode = this.fetchMode
        val filters = this.filters
        if (filters.isNullOrEmpty() || fetchMode == null) {
            this.validTill = all.size
            return
        }

        // We need to fetch row details to apply the filters and reduce the result set,
        // until we have at least valid data till the given end.
        val all = this.all
        var i = this.validTill
        var fetched_till = i
        var removed = 0 // amount of valid entries before i = i - removed
        while (i < all.size) {
            // Fetch more.
            if (i <= fetched_till) {
                val available = i - removed
                val to = min(((end - available + 50) * 1.1).toInt(), all.size)
                session.fetchTuples(all, from = i, to = to, mode = fetchMode)
                fetched_till = to
            }
            var row = all[i]
            for (f in filters) {
                if (f == null) continue
                if (row == null) continue
                row = f.call(row)
                if (row == null) {
                    removed++
                    break
                }
            }
            all[i] = row
            i++
            val available = i - removed
            if (available >= end) break
        }

        val newList = ResultTupleList()
        newList.setCapacity(all.size - removed)
        // Copy everything that is not null
        while (i < all.size) {
            val row = all[i++]
            if (row != null) newList.add(row)
        }
        this.all = newList
        this.end = min(this.end, newList.size)
        this.validTill = min(end, newList.size)
        this._result = null
    }

    override fun isComplete(): Boolean = !incomplete && validTill >= tuples.size

    override fun isPartial(): Boolean = !incomplete && validTill < tuples.size

    override fun isIncomplete(): Boolean = incomplete

    override fun createHandle(start: Int, end: Int): String? {
        if (incomplete) throw NakshaException(ILLEGAL_STATE, "The result-set is incomplete, can't create a handle")
        TODO("createHandle is not yet implemented")
    }
}