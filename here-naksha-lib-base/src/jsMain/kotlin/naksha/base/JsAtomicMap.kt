package naksha.base

class JsAtomicMap<K: Any, V:Any> : HashMap<K,V>(), AtomicMap<K, V> {
    override fun putIfAbsent(key: K, value: V): V? {
        if (!this.containsKey(key)) {
            this[key] = value
            return null
        }
        return this[key]
    }

    override fun remove(key: K, value: V): Boolean {
        if (this.containsKey(key) && this[key] === value) {
            this.remove(key)
            return true
        }
        return false
    }

    override fun replace(key: K, oldValue: V, newValue: V): Boolean {
        if (this.containsKey(key) && this[key] === oldValue) {
            this[key] = newValue
            return true
        }
        return false
    }
}