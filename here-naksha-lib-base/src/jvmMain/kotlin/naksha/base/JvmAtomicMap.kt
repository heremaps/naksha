package naksha.base

import java.util.concurrent.ConcurrentHashMap

class JvmAtomicMap<K, V> : ConcurrentHashMap<K, V>(), AtomicMap<K, V>