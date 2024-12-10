package naksha.base

import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass

open class JvmListProxy<T : Any>(javaClass: Class<T>) :
    ListProxy<T>(Reflection.getOrCreateKotlinClass(javaClass) as KClass<out T>)