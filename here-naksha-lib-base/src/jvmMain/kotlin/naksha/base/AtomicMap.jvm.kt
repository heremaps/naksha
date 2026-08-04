package naksha.base

actual open class AtomicMap<K: Any, V: Any>: MutableMap<K, V> {
    private val delegate = java.util.concurrent.ConcurrentHashMap<K, V>()

    actual override fun putIfAbsent(key: K, value: V): V? = super.putIfAbsent(key, value)

    actual fun compareAndSet(key: K, oldValue: V?, newValue: V?): Boolean {
        if (newValue == null) {
            if (oldValue == null) return !containsKey(key)
            return remove(key, oldValue)
        }
        if (oldValue == null) {
            return putIfAbsent(key, newValue) == null
        }
        return replace(key, oldValue, newValue)
    }

    actual fun putOrRemove(key: K, newValue: V?): V?
        = if (newValue == null) remove(key) else put(key, newValue)

    actual override val keys: MutableSet<K>
        get() = delegate.keys
    actual override val values: MutableCollection<V>
        get() = delegate.values
    actual override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = delegate.entries
    actual override fun put(key: K, value: V): V? = delegate.put(key, value)
    actual override fun remove(key: K): V? = delegate.remove(key)
    actual override fun putAll(from: Map<out K, V>) = delegate.putAll(from)
    actual override fun clear() = delegate.clear()
    actual override val size: Int
        get() = delegate.size
    actual override fun isEmpty(): Boolean = delegate.isEmpty()
    actual override fun containsKey(key: K): Boolean = delegate.containsKey(key)
    actual override fun containsValue(value: V): Boolean = delegate.containsValue(value)
    actual override fun get(key: K): V? = delegate.get(key)

}