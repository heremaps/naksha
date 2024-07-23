@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.model.Guid
import naksha.model.IStorage
import naksha.model.NakshaFeatureProxy
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A result-set as returned by a [Request]. The storage may not fetch the full [rows][naksha.model.Row] until the [rows] method is invoked, except a [Request.resultFilter] was given, which is applied earlier, and requires fetching all parts specified in the [RowOptions] of the [Request].
 * @property storage the storage that returned the result-set.
 * @property rowOptions the read-options as provided in the [Request].
 */
@JsExport
abstract class ResultSet(
    @JvmField val storage: IStorage,
    @JvmField val rowOptions: RowOptions
) {
    /**
     * The fetch options for the first invocation of [rows]. Changing the property may raise an [IllegalStateException], if changing is not supported, e.g. because the rows have already been fetched.
     */
    var fetchOptions: RowOptions = rowOptions

    /**
     * Tests if the rows are already fetched from the storage. If this is not yet done, it is allowed to change the [fetchOptions].
     * @return _true_ if the rows are already fetched.
     */
    abstract fun isFetched(): Boolean

    /**
     * Returns the handle of the result-set to fetch more results; if any.
     * @return the handle of the result-set to fetch more results; if any.
     */
    abstract fun handle(): String?

    /**
     * Returns the amount of features being part of the result-set.
     * @return the amount of features being part of the result-set.
     */
    abstract fun size(): Int

    /**
     * Returns the [Guid's][Guid] of the features being part of the result-set.
     *
     * Beware that the [Guid] does as well contain the [Guid.collectionId] and [Guid.featureId].
     * @return the [Guid's][Guid] of the features being part of the result-set.
     */
    abstract fun guids(): List<Guid>

    /**
     * If not yet done, fetches the rows from the storage, and returns a mutable list of them.
     *
     * The returned list is mutable and always the same one, no copy is being made. Beware that the [fetchOptions] are only applied when called for the first time, and even then it is not guaranteed, because the rows could have been fetched already.
     *
     * Multiple calls to this method will always return the fist result.
     * @return the result-set rows.
     */
    abstract fun rows(): MutableList<ResultRow>

    /**
     * Convert all rows into features, and return a mutable list of them.
     *
     * The method always returns the same mutable list, no copy is being made. Internally the method will invoke [rows] and then convert all rows into features, returning the mutable list with the features. Beware, when the returned list is modified, the content of this result-set is modified as well!
     *
     * Multiple calls to this method will always return the fist result.
     * @return all rows converted into features, and return as a mutable list.
     */
    abstract fun features(): MutableList<NakshaFeatureProxy>
}