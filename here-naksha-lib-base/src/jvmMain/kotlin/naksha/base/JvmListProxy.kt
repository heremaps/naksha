package naksha.base

open class JvmListProxy<T : Any>(javaClass: Class<T>) :
    PTypedArray<T>(javaClass.kotlin)