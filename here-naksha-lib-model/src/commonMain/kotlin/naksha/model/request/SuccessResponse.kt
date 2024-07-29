@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.model.NakshaFeatureList
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Success response, means all operations succeeded, and it's safe to commit the transaction.
 * @property resultSet the result-set as returned by the storage.
 */
@JsExport
open class SuccessResponse() : Response() {

    companion object SuccessResponse_C {
        private val RESULT_SET = NotNullProperty<SuccessResponse, ResultSet>(ResultSet::class) {  _,_ -> ResultSet() }
        private val FEATURES = NotNullProperty<SuccessResponse, NakshaFeatureList>(NakshaFeatureList::class) { _,_ ->
            // TODO: Ask the result-set to generate the list!
            NakshaFeatureList()
        }
    }

    /**
     * Warning, client that want to process the
     */
    override fun resultSize(): Int = resultSet.size

    /**
     * The result-set as returned by the storage.
     *
     * Using the result-set allows a more fine-grained control of the result processing. The result-set can be bigger than what was originally requested, so if the client provided a [ReadRequest.limit], the storage may still have produced a full result-set, which means it may have much more rows available. This happens always, when [ReadRequest.returnHandle] is _true_ or [ReadFeatures.orderBy] where given.
     */
    open val resultSet by RESULT_SET

    /**
     * The rows converted into features.
     *
     * Beware, this property is generated when accessed for the first time. That means, before the client tries to serialize the response, it should read the features property ones.
     */
    open var features by FEATURES

    var handle: String?
}