@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.fn.Fn1
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * Base request class.
 */
@JsExport
abstract class Request<SELF : Request<SELF>> {
    /**
     * Request options, if _null_, then all parts of a row should be available to the [result filters][resultFilter], and in the [result-set][ResultSet].
     */
    @JvmField
    var rowOptions: RowOptions = RowOptions()

    /**
     * Change the row-options.
     * @param rowOptions the options to use.
     * @return this.
     */
    @Suppress("UNCHECKED_CAST")
    fun withRowOptions(rowOptions: RowOptions): SELF {
        this.rowOptions = rowOptions
        return this as SELF
    }

    /**
     * Change the row-options by invoking a setter.
     * @param setter the setter to call upon the existing options, should return the new options.
     * @return this.
     */
    @Suppress("UNCHECKED_CAST")
    @JsName("onOptions")
    fun rowOptions(setter: Fn1<RowOptions, RowOptions>): SELF {
        this.rowOptions = setter.call(rowOptions)
        return this as SELF
    }


    /**
     * The resultFilter is a list of lambdas, that are invoked by the storage for every row that should be added into the results of the response. The method can inspect the row, and should return either the unmodified row, or a modified version to be added to the response or null, if the row should be removed from the response.
     *
     * The filter lambdas are called in LIFO order (last in, first out). The output each the lambda is used as input for the next one. Therefore, only if all filters return a valid new row, the last returned row will be added to the response. This means, each filter can modify the row, or cause it to be removed from the result-set.
     *
     * Beware that the filters are not serializable, therefore they can only be executed with a storage in the same process and not with foreign storages.
     *
     * - When [RowOptions.feature] is _false_, the feature is not read from the database (`row.feature` will be null).
     * - When [RowOptions.geometry] is _false_, the geometry is not read from the database (`row.geometry` will be null).
     * - When [RowOptions.refPoint] is _false_, the reference-point is not read from the database (`row.geo_ref` will be null).
     * - When [RowOptions.tags] is _false_, the tags are not read from the database (`row.tags` will be null).
     * - When [RowOptions.meta] is _false_, no meta-data is read from the database (`row.meta` will be null).
     *
     * Beware: If the filters ([RowOptions.feature], [RowOptions.geometry], [RowOptions.refPoint], [RowOptions.tags] or [RowOptions.meta]) are _false_, these values are not be read from the database, and therefore the result-filters will not be able to process these values either!
     */
    @JvmField
    var resultFilter: MutableList<Fn1<ResultRow, ResultRow>> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    fun addResultFilter(filter: Fn1<ResultRow, ResultRow>): SELF {
        this.resultFilter.add(filter)
        return this as SELF
    }

    @Suppress("UNCHECKED_CAST")
    fun addResultFilters(vararg filters: Fn1<ResultRow, ResultRow>): SELF {
        for (filter in filters) this.resultFilter.add(filter)
        return this as SELF
    }

    /**
     * Copy all properties of this request into the given target and return the target.
     * @param copy the target to receive the copy.
     * @return the given copy target.
     */
    open fun copyTo(copy: SELF): SELF {
        copy.rowOptions = this.rowOptions
        copy.resultFilter = this.resultFilter.toMutableList()
        return copy
    }
}