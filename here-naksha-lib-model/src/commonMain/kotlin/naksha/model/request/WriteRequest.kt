@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import kotlin.js.JsExport

/**
 * Ask the storage to perform a set of write operations.
 *
 * Example:
 * ```kotlin
 * val req = WriteRequest()
 * req.add(Write().createFeature(null, collection.id, feature))
 * ...
 * ```
 * ```java
 * final WriteRequest req = new WriteRequest();
 * req.add(new Write()
 *         .createFeature(null, collection.getId(), feature));
 * ...
 * ```
 */
@JsExport
open class WriteRequest : Request() {
    companion object WriteRequest_C {
        private val WRITE_LIST = NotNullProperty<WriteRequest, WriteList>(WriteList::class)
        private val BOOLEAN = NotNullProperty<WriteRequest, Boolean>(Boolean::class) { _, _ -> false }
    }

    override fun defaultRowOptions() : ReturnColumns = ReturnColumns.none().withMeta(true)

    /**
     * All writes to perform.
     *
     * It might have, operations of different types (insert/update/etc.), and to different collections.
     */
    var writes by WRITE_LIST

    fun add(op: Write): WriteRequest {
        writes.add(op)
        return this
    }

    /**
     * By default, a write request will return either a [SuccessResponse] or an [ErrorResponse], but the storage does not return details about the outcome.
     *
     * This improves the performance, because some operations can be done directly within the database, without reading any data back, like for example deleting a feature.
     *
     * However, if results are needed, this option can be enabled. Beware, that when enabling this option, the default [returnColumns] are limited to return only the [metadata][naksha.model.Metadata]. The reason behind this is, that the client normally only need this, it can simply replace the [metadata][naksha.model.Metadata] in the feature with the one returned, and has an up-to-date feature.
     */
    var returnResults by BOOLEAN

    /**
     * By default, the response will return the write results in any order.
     *
     * If needed, it's possible to change this behaviour by setting this flag to _true_, in such case response will return rows in the order in which the write instructions where given. If _false_ (_default_), the results are returned is most convenient order for the storage, which is less effort for the storage in some cases, because it does not have to order results.
     */
    var strictOrder by BOOLEAN

}