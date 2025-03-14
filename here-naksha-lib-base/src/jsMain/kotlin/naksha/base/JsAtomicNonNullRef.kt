@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("SENSELESS_COMPARISON")

package naksha.base

@JsExport
class JsAtomicNonNullRef<R: Any> internal constructor(private var value: R) : AtomicNonNullRef<R> {
    init {
        require(value != null)
    }

    override fun get(): R = value

    override fun compareAndSet(expectedValue: R, newValue: R): Boolean {
        require(expectedValue != null && newValue != null)
        if (value === expectedValue) {
            value = newValue
            return true
        }
        return false
    }

    override fun getAndSet(newValue: R): R {
        require(newValue != null)
        val oldValue = value
        value = newValue
        return oldValue
    }

    override fun set(newValue: R) {
        require(newValue != null)
        value = newValue
    }
}