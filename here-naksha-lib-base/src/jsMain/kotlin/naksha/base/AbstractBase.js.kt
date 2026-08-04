package naksha.base

actual abstract class AbstractBase : IBase {
    companion object AbstractBase_C {
        private const val ATOMIC = 1
        private const val READ_ONLY = 2
        private const val IMMUTABLE = 4
    }

    actual fun startMutate() {
        if (readOnly) throw illegalState("The number is read-only")
    }
    protected actual fun endMutate() {
        val current_state = this.state
        // This code allows overflow of version back to zero without any influence on lower 3-bit of state.
        this.state = (((current_state ushr 3) + 1) shl 3) or (current_state and 7)
    }

    actual override var atomic: Boolean
        get() = (state and ATOMIC) == ATOMIC
        set(value) {
            if (((state and ATOMIC) == ATOMIC) == value) return // state unchanged
            if (immutable) throw illegalState("Object is immutable")
            state = if (value) state or ATOMIC else state and ATOMIC.inv()
        }
    actual override fun withAtomic(enable: Boolean): AbstractBase {
        this.atomic = enable
        return this
    }

    actual override var readOnly: Boolean
        get() = (state and READ_ONLY) == READ_ONLY
        set(value) {
            if (((state and READ_ONLY) == READ_ONLY) == value) return // state unchanged
            if (immutable) throw illegalState("Object is immutable")
            state = if (value) state or READ_ONLY else state and READ_ONLY.inv()
        }
    actual override fun withReadOnly(enable: Boolean): AbstractBase {
        this.readOnly = enable
        return this
    }

    actual override var immutable: Boolean
        get() = (state and IMMUTABLE) == IMMUTABLE
        set(value) {
            if (((state and IMMUTABLE) == IMMUTABLE) == value) return // state unchanged
            if (immutable) if (!value) throw illegalState("Object is immutable") else return
            state = state or IMMUTABLE
        }
    actual override fun withImmutable(enable: Boolean): AbstractBase {
        this.immutable = enable
        return this
    }

    actual override val version: Int
        get() = state ushr 3

    internal var state: Int = 0
}