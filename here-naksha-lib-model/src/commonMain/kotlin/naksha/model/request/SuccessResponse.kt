@file:Suppress("OPT_IN_USAGE", "DEPRECATION")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.NakshaFeatureList
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Success response, means all operations succeeded, and it's safe to commit the transaction.
 */
@JsExport
open class SuccessResponse @Deprecated("Use secondary", ReplaceWith("SuccessResponse(rs)")) constructor()
    : Response() {

    /**
     * Create a success response from a result-set.
     * @param rs the result-set from which to create a success response.
     */
    @JsName("ofResultSet")
    constructor(rs: ResultSet) : this() {
        setRaw("resultSet", rs)
    }

    companion object SuccessResponse_C {
        private val RESULT_SET_NULL = NullableProperty<SuccessResponse, ResultSet>(ResultSet::class)
        private val ROWS = NotNullProperty<SuccessResponse, ResultTupleList>(ResultTupleList::class) { self, _ ->
            val rs = self.resultSet
            rs?.result() ?: ResultTupleList()
        }
        @Suppress("UNCHECKED_CAST")
        private val FEATURES = NotNullProperty<SuccessResponse, NakshaFeatureList>(NakshaFeatureList::class) { self, _ ->
            val features = NakshaFeatureList()
            val rs = self.resultSet
            if (rs != null) {
                val rows = rs.result()
                rs.storage().fetchRows(rows as List<ResultTuple>)
                for (row in rows) {
                    val f = row?.feature
                    if (f != null) features.add(f)
                }
            }
            features
        }
        private val HANDLE_NULL = NullableProperty<SuccessResponse, String>(String::class) { self, _ ->
            self.resultSet?.createHandle()
        }
    }

    override fun resultSize(): Int = resultSet?.resultSize() ?: features.size

    /**
     * The [result-set][ResultSet] as returned by the storage.
     *
     * If this response is restored from a serialized one, it will not have a result-set.
     *
     * Using the result-set directly allows a more fine-grained control of the result processing. The result-set can be bigger than what was originally requested, so if the client provided a [ReadRequest.limit], the storage may still have produced a full result-set, which means it may have much more rows available. This happens always, when [ReadRequest.returnHandle] is _true_ or [ReadFeatures.orderBy] was given.
     */
    open val resultSet by RESULT_SET_NULL

    /**
     * Return the result rows being part of the response.
     *
     * This property is not serializable.
     */
    open val rows by ROWS

    /**
     * The result rows converted into features.
     *
     * This property is generated when accessed for the first time. That means, before the client tries to serialize the response, it should read the features property ones.
     */
    open var features by FEATURES

    /**
     * The handle to fetch more results, if any is available.
     */
    open var handle by HANDLE_NULL
}