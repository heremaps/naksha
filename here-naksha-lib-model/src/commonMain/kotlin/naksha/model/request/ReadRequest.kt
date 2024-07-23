@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * All read-requests should extend this base class.
 */
@JsExport
abstract class ReadRequest<SELF: ReadRequest<SELF>>: Request<SELF>() {

    /**
     * Limit the size of the response, if _null_ a default defined by the storage is used.
     */
    @JvmField
    var limit: Int? = null

    /**
     * Changes the maximal number of features to return at ones.
     * @param limit the maximum number of features to return at ones.
     * @return this.
     */
    @Suppress("UNCHECKED_CAST")
    fun withLimit(limit: Int?): SELF {
        this.limit = limit
        return this as SELF
    }

    /**
     * When [limit] is reached, and this property is _true_, then a [success response][naksha.model.response.SuccessResponse] will have a [handle][naksha.model.response.SuccessResponse.handle] that allows fetching more data (beyond the limit). This can be expensive to generate, and should be avoided.
     */
    @JvmField
    var returnHandle: Boolean = false

    /**
     * Return a [SuccessResponse.handle][naksha.model.response.SuccessResponse.handle], when the `limit` is reached.
     */
    @Suppress("UNCHECKED_CAST")
    fun withReturnHandle(): SELF {
        returnHandle = true
        return this as SELF
    }

    override fun copyTo(copy: SELF): SELF {
        super.copyTo(copy)
        copy.limit = limit
        copy.returnHandle = returnHandle
        return copy
    }
}