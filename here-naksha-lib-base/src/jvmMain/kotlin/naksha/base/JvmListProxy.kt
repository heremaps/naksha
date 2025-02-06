package naksha.base

open class JvmListProxy<T : Any>(javaClass: Class<T>) :
    ListProxy<T>(javaClass.kotlin)