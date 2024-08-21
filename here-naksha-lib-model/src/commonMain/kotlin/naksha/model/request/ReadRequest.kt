@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.FetchMode
import kotlin.js.JsExport

/**
 * All read-requests should extend this base class.
 * @since 3.0.0
 */
@JsExport
open class ReadRequest : Request() {
    companion object ReadRequestCompanion {
        private val INT_NULL = NullableProperty<ReadRequest, Int>(Int::class)
        private val BOOLEAN =
            NullableProperty<ReadRequest, Boolean>(Boolean::class) { _, _ -> false }
        private val FETCH_MODE =
            NotNullProperty<Request, FetchMode>(FetchMode::class) { _, _ -> FetchMode.FETCH_ALL }
    }

    override fun defaultRowOptions(): ReturnColumns = ReturnColumns.all()

    /**
     * A soft-cap, so the amount of rows the client needs.
     *
     * If _null_, the storage will automatically decide for some soft-cap value. If all results are needed, setting it to [Int.MAX_VALUE] should be considered. If the soft-cap (_limit_) is bigger than what the storage supports as hard-cap, the hard-cap is used by the storage. For example `lib-psql` has a default hard-cap of 1,000,000, and therefore will never fetch more than one million rows, even when requested.
     *
     * To query more than the hard-cap of a storage, a streaming processing is needed. The interface for this is not yet designed, but may come with later model specifications.
     * @since 3.0.0
     */
    var limit by INT_NULL

    /**
     * A parameter to tell the storage if the client wants a handle.
     *
     * If _true_, the storage need to always generate the full result-set. It does not need to load all features into memory all the time, but as soon as a handle should be generated, an ordered result-set is needed, which requires to fetch all results to order them. Therefore, the storage at least need to generate the list of all [row identifiers][naksha.model.TupleNumber] being part of the result, then ordering them, optimally only by `version` and `uid`, which does not require to load all the row data. This is needed to be able to generate a handle from it (so to seek within the result-set).
     *
     * If the storage need to apply any filter-lambdas or perform a _property_ search (which is as well an intrinsic filtering lambda), it at least need to load as many results as the [limit] describes from the storage into memory.
     *
     * A middle ground is to order by data that is part of the [metadata][naksha.model.Metadata]. This requires the storage to load all rows with their metadata into memory, but it does not yet need to load the feature itself, nor the geometry, tags or attachment.
     *
     * The worst case is an order by something very custom, when requested, the storage needs not only the [row identifiers][naksha.model.TupleNumber], but the full rows with all data. In that case all results are loaded into memory, filtered, and eventually ordered.
     * @since 3.0.0
     */
    var returnHandle by BOOLEAN

    /**
     * How do we want the fetch to be performed
     */
    var fetchMode by FETCH_MODE
}