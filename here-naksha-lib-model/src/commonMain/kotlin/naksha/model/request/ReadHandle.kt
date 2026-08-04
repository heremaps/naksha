@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A special request to read more results from a handle.
 *
 * Requires a handle returned by any previously executed [ReadRequest]. This request may optionally ask for another handle, by setting the [returnHandle] property to `true`. If the [limit] is not explicitly set, the same limit as encoded in the handle is used, which simplifies pagination, when the page size should be stable.
 * @property handle the handle to read from.
 */
@JsExport
open class ReadHandle() : ReadRequest() {

    /**
     * Create a new read handle request.
     * @param handle the handle to continue reading.
     */
    @JsName("of")
    constructor(handle: ByteArray) : this() {
        this.handle = handle
    }

    companion object ReadHandle_C {
        private val BYTEARRAY_NULLABLE = NullableProperty<ReadRequest, ByteArray>(ByteArray::class)
    }

    /**
     * The handle from the previous request to continue.
     *
     * The handle contains all information the storage needs to continue a previous request, so fetching more results. A
     * @since 3.0
     */
    var handle: ByteArray? by BYTEARRAY_NULLABLE
}