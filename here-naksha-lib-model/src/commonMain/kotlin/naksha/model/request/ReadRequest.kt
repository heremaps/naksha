@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NullableProperty
import kotlin.js.JsExport

/**
 * All read-requests should extend this base class.
 *
 * @since 3.0.0
 */
@JsExport
open class ReadRequest : Request() {
    companion object ReadRequestCompanion {
        private val INT_NULLABLE = NullableProperty<ReadRequest, Int>(Int::class)
        private val BOOLEAN = NullableProperty<ReadRequest, Boolean>(Boolean::class) { _, _ -> false }
    }

    /**
     * A soft-cap, so the amount of [Tuple][naksha.model.Tuple] the client needs.
     *
     * If `null`, the storage will automatically select a limit. To query more than the [hard-cap][naksha.model.IStorage.hardCap] of a storage, a streaming processing is needed or handles need to be used.
     * @since 3.0.0
     */
    var limit: Int? by INT_NULLABLE

    /**
     * A parameter to tell the storage if the client wants a handle.
     *
     * If `true`, the storage need to always generate the full result-set. It does not need to load all objects into memory all the time, but as soon as a handle should be generated, an ordered result-set is needed, which requires to fetch all results to order them. Therefore, the storage at least need to generate the list of all [tuple-numbers][naksha.base.TupleNumber] being part of the result, then ordering them. This is needed to be able to generate a handle from it (so to seek within the result-set).
     *
     * If the storage need to apply any filter-lambdas or perform a _property_ search (which is as well an intrinsic filtering lambda), it at least need to load as many results as the [limit] describes from the storage into memory.
     *
     * Therefore, unless really needed, handles should be avoided as they can make result generation much slower.
     * @since 3.0.0
     */
    var returnHandle by BOOLEAN
}