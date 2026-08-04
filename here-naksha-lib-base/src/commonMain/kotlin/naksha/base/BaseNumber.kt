package naksha.base

/**
 * Base class for all numbers, implementing [IBase] interface and behavior.
 * @since 3.0
 * @see BaseInt
 * @see BaseLong
 * @see BaseDouble
 */
expect abstract class BaseNumber: Number, IBase {
    override var atomic: Boolean
    override fun withAtomic(enable: Boolean): BaseNumber
    override var readOnly: Boolean
    override fun withReadOnly(enable: Boolean): BaseNumber
    override var immutable: Boolean
    override fun withImmutable(enable: Boolean): BaseNumber
    override val version: Int

    /**
     * Called by extending classes before mutating.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if [readOnly].
     */
    protected fun startMutate()

    /**
     * Called by extending classes after mutating, updates the [version].
     * @since 3.0
     */
    protected fun endMutate()
}