package naksha.base

open class JvmMapProxy<K: Any, V: Any>(keyClass: Class<K>, valueClass: Class<V>):
    MapProxy<K, V>(keyClass.kotlin, valueClass.kotlin)