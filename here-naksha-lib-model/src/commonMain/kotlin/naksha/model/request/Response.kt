@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.geo.GeoCollection
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A response to a [Request].
 *
 * If this response is for a write request with [WriteRequest.returnResults] being _true_, the client signals that it is not interested in the result (except for either being success or failure), and the database should not generate result rows. This improves write throughput, because no data must be returned (often it simplifies the write itself, e.g. when deleting rows, they do not need to be read from the database).
 */
@JsExport
open class Response : GeoCollection() {

    companion object Response_C {
        /**
         * The [PlatformType] of [Response].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Response::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * The size of the underlying platform object, so the hash-map, **not the amount of results**!
     *
     * ## Warning
     * This does not return the amount of results, please use [length] for this!
     * @since 3.0
     * @see length
     */
    @Deprecated(message = "You very likely mean length", replaceWith = ReplaceWith("length"), level = DeprecationLevel.WARNING)
    override val size: Int = super.size

    /**
     * The amount of results being in the response.
     *
     * @since 3.0
     */
    open val length: Int
        get() = 0

    /**
     * Returns the amount of the result being in the response.
     * @since 3.0
     * @see [length]
     */
    @Deprecated(message = "Please use length", replaceWith = ReplaceWith("length"), level = DeprecationLevel.ERROR)
    fun resultSize(): Int = length
}