@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.AnyObject
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.objects.PACKAGE_NAME
import naksha.model.request.notification.TuplesByTxn
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Base request class.
 * @since 3.0
 */
@JsExport
open class Request : AnyObject() {
    companion object Request_C {
        /**
         * The [PlatformType] of [Request].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Request::class).withPackageName(PACKAGE_NAME)

        private val RETURN_OPTIONS = NotNullProperty<Request, ReturnColumns>(ReturnColumns.TYPE) { self, _ -> self.defaultRowOptions() }
        private val RESULT_FILTER_LIST = NotNullProperty<Request, ResultFilterList>(ResultFilterList.TYPE)
    }

    /**
     * The method being called to create the initial [returnColumns].
     * @return the initial row options.
     * @since 3.0
     */
    protected open fun defaultRowOptions() : ReturnColumns = ReturnColumns.all()

    /**
     * Options of what data is needed by the [resultFilters] and the client (defaults for [SuccessResponse.features]).
     *
     * The columns of a [tuple][naksha.model.Tuple] that are needed in the [resultFilters]. Actually, if any [resultFilters] are set, this causes the API to invoke [naksha.model.ISession.loadTuples] before delivering the [FeatureTuple] to the [resultFilters].
     * @since 3.0
     */
    var returnColumns by RETURN_OPTIONS

    /**
     * A list of lambdas, that should be invoked by the storage for every row that should be added into a result-set. The method can inspect the row, and should return either the unmodified row, a modified version to be added to the response, or _null_, if the row should be removed from the result-set.
     *
     * The filter lambdas are called in LIFO order (last in, first called _(out)_). The output of each lambda is used as input for the previous one. Therefore, only if all filter return a valid new row, the last returned row will be added to the response. This means, each filter can modify the row, or cause it to be removed from the result-set.
     *
     * Adding filtering lambdas conflicts slightly with the [ReadRequest.limit], because the filter can remove an arbitrary amount of features, the storage will need to generate a full result-set, and apply the filter the result-set until it has enough results to fulfill the requested [ReadRequest.limit].
     * @since 3.0
     */
    var resultFilters by RESULT_FILTER_LIST
}