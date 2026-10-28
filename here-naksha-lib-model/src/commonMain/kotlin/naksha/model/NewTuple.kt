package naksha.model

import naksha.base.TupleNumber
import kotlin.js.JsExport

/**
 * An immutable wrapper around a newly encoded [Tuple] and the expected predecessor state.
 *
 * Persisted and cached [Tuple]s represent immutable states and must not carry mutable operation
 * expectations. The expected predecessor _(the state the caller believes the feature was in before
 * the modification)_ is therefore transported separately in this wrapper. A non-null
 * [previousTupleNumber] requests an atomic update; `null` selects an unconditional update.
 *
 * This is not a persisted tuple format and not a cache entry.
 * @since 3.0
 */
@JsExport
data class NewTuple(
    /**
     * The encoded new state.
     * @since 3.0
     */
    val tuple: Tuple,

    /**
     * The expected predecessor tuple-number; `null` when the operation is unconditional.
     * @since 3.0
     */
    val previousTupleNumber: TupleNumber? = null,
)
