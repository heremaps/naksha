package naksha.base

/**
 * The interface for custom data types of `lib-base`.
 * @since 3.0
 * @see BaseNumber
 * @see AbstractBase
 */
interface IBase {
    /**
     * When toggled-on, the object is atomic mutable, therefore, safe to be accessed by multiple threads.
     *
     * Generally, the read-performance of atomic entities is the same as for non-atomic ones. If specific memory access patterns are wished, memory fences should be set manually. All reads will be performed plain by default.
     *
     * For writes this is not true. Atomicity is implemented by a copy-on-write strategy. Every operation, no matter if read or write, will first copy the current reference to the internal state to stack, then all actions will be performed, and eventually a compare-and-set operation is done to change the state. In case of concurrent modification, one thread will succeed, all others will have to repeat the operation.
     *
     * When atomic is disabled, the general operation stays the same, but the modifications are done directly on the current state, an replacement of the _HEAD_ reference only happens, when a new one is needed. Therefore, copies are only made on demand, while in atomic mode every modification
     *
     * This means, the atomic state is perfectly fine, when objects should be shared for read between threads and only rarely been modified. However, it will become a real issue, if used in a write-heavy situation.
     * @since 3.0
     */
    var atomic: Boolean

    /**
     * Change the [atomic] state and return this.
     * @since 3.0
     * @see atomic
     */
    fun withAtomic(enable: Boolean = true): IBase

    /**
     * When toggled on, the object is read-only.
     *
     * Any try to change properties of the object will raise an [NakshaException] with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE].
     * @since 3.0
     */
    var readOnly: Boolean

    /**
     * Change the [readOnly] state and return this.
     * @since 3.0
     * @see readOnly
     */
    fun withReadOnly(enable: Boolean = true): IBase

    /**
     * When toggled on, [atomic] and [readOnly] toggles are frozen.
     *
     * This state can only be enabled ones, after done, the object state _([atomic], [readOnly])_ becomes immutable.
     * @since 3.0
     */
    var immutable: Boolean

    /**
     * Change the [immutable] state and return this.
     * @since 3.0
     * @see immutable
     */
    fun withImmutable(enable: Boolean = true): IBase

    /**
     * A counter being incremented after every mutation.
     * @since 3.0
     */
    val version: Int
}