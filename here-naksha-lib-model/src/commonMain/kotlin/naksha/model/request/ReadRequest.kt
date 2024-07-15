@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import kotlin.js.JsExport
import kotlin.jvm.JvmField

@JsExport
abstract class ReadRequest<SELF:Request<SELF>>: Request<SELF>() {

    /**
     * Limit of single response.
     *
     * The result might have `handle` to fetch more rows.
     */
    @JvmField
    var limit: Int = DEFAULT_LIMIT

    fun withLimit(limit: Int): ReadRequest<SELF> {
        this.limit = limit
        return this
    }

    companion object ReadRequestCompanion {
        const val DEFAULT_LIMIT = 100_000
    }
}