package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

actual abstract class AbstractBase: IBase {
    companion object AbstractBase_C {
        private const val ATOMIC = 1
        private const val READ_ONLY = 2
        private const val IMMUTABLE = 4
        private val stateHandle: VarHandle = MethodHandles
            .privateLookupIn(AbstractBase::class.java, MethodHandles.lookup())
            .findVarHandle(AbstractBase::class.java, "state", Int::class.java)
    }

    actual fun startMutate() {
        if (readOnly) throw illegalState("The object is read-only")
    }

    actual fun endMutate() {
        if (!atomic) {
            val current_state = this.state
            // This code allows overflow of version back to zero without any influence on lower 3-bit of state.
            this.state = (((current_state ushr 3) + 1) shl 3) or (current_state and 7)
        } else {
            while (true) {
                val current_state = state
                val new_state = (((current_state ushr 3) + 1) shl 3) or (current_state and 7)
                if (stateHandle.compareAndSet(this, current_state, new_state)) return
                // if the CAS failed, the current value is in our cache, so the next plain read will get the real state!
            }
        }
    }

    actual override var atomic: Boolean
        get() = (state and ATOMIC) == ATOMIC
        set(value) {
            while (true) {
                val current_state = state
                if (((current_state and ATOMIC) == ATOMIC) == value) return // state unchanged
                if ((current_state and IMMUTABLE) == IMMUTABLE) throw illegalState("Object is immutable")
                val new_state = if (value) current_state or ATOMIC else current_state and ATOMIC.inv()
                if (stateHandle.compareAndSet(this, current_state, new_state)) return
                // if the CAS failed, the current value is in our cache, so the next plain read will get the real state!
            }
        }
    actual override fun withAtomic(enable: Boolean): AbstractBase {
        this.atomic = enable
        return this
    }

    actual override var readOnly: Boolean
        get() = (state and READ_ONLY) == READ_ONLY
        set(value) {
            while (true) {
                val current_state = state
                if (((current_state and READ_ONLY) == READ_ONLY) == value) return // state unchanged
                if ((current_state and IMMUTABLE) == IMMUTABLE) throw illegalState("Object is immutable")
                val new_state = if (value) current_state or READ_ONLY else current_state and READ_ONLY.inv()
                if (stateHandle.compareAndSet(this, current_state, new_state)) return
                // if the CAS failed, the current value is in our cache, so the next plain read will get the real state!
            }
        }
    actual override fun withReadOnly(enable: Boolean): AbstractBase {
        this.readOnly = enable
        return this
    }

    actual override var immutable: Boolean
        get() = (state and IMMUTABLE) == IMMUTABLE
        set(value) {
            while (true) {
                val current_state = state
                if (((current_state and IMMUTABLE) == IMMUTABLE) == value) return // state unchanged
                if ((current_state and IMMUTABLE) == IMMUTABLE) throw illegalState("Object is immutable")
                val new_state = if (value) current_state or IMMUTABLE else current_state and IMMUTABLE.inv()
                if (stateHandle.compareAndSet(this, current_state, new_state)) return
                // if the CAS failed, the current value is in our cache, so the next plain read will get the real state!
            }
        }
    actual override fun withImmutable(enable: Boolean): AbstractBase {
        this.immutable = enable
        return this
    }

    actual override val version: Int
        get() = state ushr 3

    /**
     * Stores in the lower 3-bit the value of [atomic], [readOnly] and [immutable], the rest is used as version.
     * @since 3.0
     */
    private var state: Int = 0
}