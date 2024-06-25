package naksha.base

import java.util.concurrent.ConcurrentHashMap

class JvmCMap<K: Any, V: Any> : ConcurrentHashMap<K, V>(), CMap<K, V>