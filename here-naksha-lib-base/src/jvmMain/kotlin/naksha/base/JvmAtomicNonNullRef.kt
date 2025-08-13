@file:Suppress("SENSELESS_COMPARISON")

package naksha.base

import java.util.concurrent.atomic.AtomicReference

internal class JvmAtomicNonNullRef<R: Any>(initial: R) : AtomicNonNullRef<R> {
    init {
        require(initial != null)
    }
    private val value = AtomicReference<R>(initial)

    override fun get(): R = value.get()

    override fun compareAndSet(expectedValue: R, newValue: R): Boolean {
        require(expectedValue != null && newValue != null)
        return value.compareAndSet(expectedValue, newValue)
    }

    override fun getAndSet(newValue: R): R {
        require(newValue != null)
        return value.getAndSet(newValue)
    }

    override fun set(newValue: R) {
        require(newValue != null)
        value.set(newValue)
    }
}
