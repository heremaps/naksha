@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import kotlin.js.JsExport
import kotlin.jvm.JvmField

@JsExport
class WriteRequest : Request<WriteRequest>() {
    /**
     * Write operations to perform.
     * It might have, operations of different types (Insert/Update/etc.) and to different collections (tables).
     */
    @JvmField
    var ops: MutableList<Write> = mutableListOf()

    fun add(op: Write): WriteRequest {
        ops.add(op)
        return this
    }

    /**
     * When noResults is set, the response will not contain any results (rows). This is the fastest way to perform a write-request.
     * You'll still get information if request succeeded or not.
     */
    @JvmField
    var noResults: Boolean = false

    fun withNoResults(): WriteRequest {
        noResults = true
        return this
    }

    /**
     * By default, the response will return rows in same order as were given in request. It's possible to change this behaviour by setting this flag to `true`, in such case response will return rows in order that is most convenient for the storage, which is less effort for the storage in some cases, because it does not have to order results.
     */
    @JvmField
    var allowRandomOrder: Boolean = false

    fun withAllowRandomOrder(): WriteRequest {
        allowRandomOrder = true
        return this
    }

    override fun copyTo(copy: WriteRequest): WriteRequest {
        super.copyTo(copy)
        copy.ops = this.ops.toMutableList()
        copy.noResults = this.noResults
        copy.allowRandomOrder = this.allowRandomOrder
        return copy
    }
}