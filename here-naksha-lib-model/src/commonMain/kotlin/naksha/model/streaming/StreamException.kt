package naksha.model.streaming

import naksha.base.NakshaError
import naksha.base.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An exception thrown either by a [Stream] implementation or consumer, if the stream reading failed. The exception contains the [StreamRequest] to recover from the error. If the error is not recoverable, then only a [NakshaException] will be thrown.
 * @since 3.0
 */
@JsExport
open class StreamException(
    /**
     * The request that can be used to recover the stream using a new [IReadSession][naksha.model.IReadSession].
     * @see naksha.model.IReadSession
     * @since 3.0
     * @see naksha.model.IStreamSession
     */
    val streamRequest: StreamRequest,

    /**
     * The error reason to forward to super class.
     * @since 3.0
     */
    error: NakshaError
) : NakshaException(error) {

    /**
     * Create a recovery stream exception based upon individual values, which will be assembled to an [NakshaError].
     * @param recoverOptions tThe options that can be used to recover the stream using a new [IReadSession][naksha.model.IReadSession].
     * @param code the error code, put into [NakshaError.code].
     * @param msg the human-readable error message, put into [message] and into [NakshaError.msg].
     * @param cause the optional cause of this error, put into [Exception.cause].
     */
    @JsName("newStreamException")
    constructor(recoverOptions: StreamRequest, code: String, msg: String, cause: Throwable? = null): this(recoverOptions, NakshaError(code, msg, cause))
}