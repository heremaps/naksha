@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.model.request.ErrorResponse
import naksha.base.NakshaError
import naksha.base.Int64
import naksha.base.MapProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.objects.PACKAGE_NAME
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Reports a processing error, but confirm that the pipeline processed the transaction up until (including) the given sequence number, and within this transaction up until (excluding) the given uid.
 *
 * If the original notification contained further transactions, these will be delivered again in a new subscription notification. If the transaction with the reported sequence number was not fully processed, only the not processed features are send again.
 */
@JsExport
class NotificationError(
    reason: NakshaError,
    val seqNumber: Int64,
    val uid: Int,
    val handlerStates: MapProxy<String, Any>
) : ErrorResponse(reason) {
    companion object NotificationError_C {
        /**
         * The [PlatformType] of [NotificationError].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NotificationError::class).withPackageName(PACKAGE_NAME)
    }
}