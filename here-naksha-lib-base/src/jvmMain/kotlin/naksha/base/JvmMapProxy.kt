package naksha.base

open class JvmMapProxy<K: Any, V: Any>(keyClass: Class<K>, valueClass: Class<V>):
    PTypedMap<K, V>(keyClass.kotlin, valueClass.kotlin)