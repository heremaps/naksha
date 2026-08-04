package naksha.base

/**
 * Abort the `forEach` iteration and return the given `value` to the `forEach` caller.
 * @param value the value to return to the caller of the `forEach`.
 * @property value the value to return to the caller of the `forEach`.
 * @since 3.0
 */
expect class ForEachAbort(value: Any?): Exception {
    /**
     * The value returned to the `forEach` caller.
     * @since 3.0
     */
    val value: Any?
}
