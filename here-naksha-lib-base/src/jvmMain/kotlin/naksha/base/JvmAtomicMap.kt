package naksha.base

import java.util.concurrent.ConcurrentHashMap

class JvmAtomicMap<K: Any, V: Any> : ConcurrentHashMap<K, V>(), AtomicMap<K, V>