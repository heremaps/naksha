package naksha.base

@JsExport
class JsAtomicRef<R: Any> internal constructor(private var value: R?) : AtomicRef<R> {
    override fun get(): R? = value

    override fun compareAndSet(expectedValue: R?, newValue: R?): Boolean {
        if (value === expectedValue) {
            value =newValue
            return true
        }
        return false
    }

    override fun getAndSet(newValue: R?): R? {
        val oldValue = value
        value = newValue
        return oldValue
    }

    override fun set(newValue: R?) {
        value = newValue
    }
}