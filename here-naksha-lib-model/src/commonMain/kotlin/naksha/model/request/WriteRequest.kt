@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.model.objects.NakshaFeature
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
     * When `noResults` is set, the response will not contain any results (rows), it will not even hold a result-set. This is the fastest way to perform a write-request. You'll still get information if request succeeded or not.
     */
    var noResults by BOOLEAN

    fun withNoResults(): WriteRequest {
        noResults = true
        return this
    }

    /**
     * By default, the response will return the rows in same order as they were given in request. It's possible to change this behaviour by setting this flag to _true_, in such case response will return rows in the order that is most convenient for the storage, which is less effort for the storage in some cases, because it does not have to order results.
     */
    var allowRandomOrder by BOOLEAN

    fun withAllowRandomOrder(): WriteRequest {
        allowRandomOrder = true
        return this
    }

}