package naksha.base

/**
 * An implementation of the [IBase] interface.
 * @since 3.0
 * @see BaseObject
 * @see BaseBool
 * @see BaseRef
 * @see BaseRefNotNull
 */
expect abstract class AbstractBase: IBase {
    override var atomic: Boolean
    override fun withAtomic(enable: Boolean): AbstractBase
    override var readOnly: Boolean
    override fun withReadOnly(enable: Boolean): AbstractBase
    override var immutable: Boolean
    override fun withImmutable(enable: Boolean): AbstractBase
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