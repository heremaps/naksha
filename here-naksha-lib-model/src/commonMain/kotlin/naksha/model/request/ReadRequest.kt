@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NullableProperty
import kotlin.js.JsExport

/**
 * All read-requests should extend this base class.
 * @since 3.0.0
 */
@JsExport
open class ReadRequest: Request() {
    companion object ReadRequestCompanion {
        private val INT_NULL = NullableProperty<ReadRequest, Int>(Int::class)
        private val BOOLEAN = NullableProperty<ReadRequest, Boolean>(Boolean::class) { _,_ -> false }
    }

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
     * A parameter to tell the storage if the client wants a handle later generated.
     *
     * If _true_, the storage need to always generate the full result-set. It does not need to load all features into memory all the time, but as soon as a handle should be generated, an ordered result-set is needed, which requires to fetch all results to order them. Therefore, the storage at least need to generate the list of all [row addresses][naksha.model.RowAddr] being part of the result, then ordering them, optimally only by `txn` and `uid`, which does not require to load the row data. This is needed to be able to generate a handle from it.
     *
     * If the storage need to apply any filtering lambdas, perform a _property_ search (which is as well an intrinsic filtering lambda), it at least need to load as many results as the [limit] describes fully from the storage.
     *
     * The worst case is an order by anything else than the transaction numbers, when requested, it needs not only the [row addresses][naksha.model.RowAddr], but the full rows with all data. In that case all results are loaded into memory, filtered, and eventually ordered.
     */
    var returnHandle by BOOLEAN
}