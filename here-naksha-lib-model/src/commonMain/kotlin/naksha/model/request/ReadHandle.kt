@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A special request to read more results from a handle.
 *
 * Requires a handle returned by any previously executed [ReadRequest]. This request may optionally ask for another handle, by setting the [returnHandle] property to _true_. If the [limit] is not explicitly set, the same limit as encoded in the handle is used, which simplifies pagination, when the page size should be stable.
 * @property handle the handle to read from.
 */
@JsExport
open class ReadHandle() : ReadRequest() {

    /**
     * Create a new read handle request.
     * @param handle the handle to continue reading.
     */
    @JsName("ReadHandleOf")
    constructor(handle: String) : this() {
        this.handle = handle
    }

    companion object ReadHandle_C {
        /**
         * The [PlatformType] of [ReadHandle].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ReadHandle::class).withPackageName(PACKAGE_NAME)

        private val STRING = NotNullProperty<ReadHandle, String>(String_TYPE) { _, _ -> "" }
    }

    /**
     * The handle to read.
     */
    var handle by STRING
}