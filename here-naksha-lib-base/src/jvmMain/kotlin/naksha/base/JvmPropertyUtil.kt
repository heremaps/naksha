package naksha.base

import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass

object JvmPropertyUtil {

    @JvmStatic
    @JvmOverloads
    fun <BEARER: AnyObject, PROPERTY: Any> notNullProperty(
        propertyType: Class<PROPERTY>,
        name: String,
        init: ((BEARER, String) -> PROPERTY)? = null
    ): NotNullProperty<BEARER, PROPERTY> {
        val propertyK = Reflection.getOrCreateKotlinClass(propertyType) as KClass<PROPERTY>
        return NotNullProperty(
            klass = propertyK,
            name = name,
            init = init
        )
    }

    @JvmStatic
    @JvmOverloads
    fun <BEARER: AnyObject, PROPERTY: Any> nullableProperty(
        propertyType: Class<PROPERTY>,
        name: String,
        autoRemove: Boolean = false,
        init: ((BEARER, String) -> PROPERTY)? = null
    ): NullableProperty<BEARER, PROPERTY> {
        val propertyK = Reflection.getOrCreateKotlinClass(propertyType) as KClass<PROPERTY>
        return NullableProperty(
            klass = propertyK,
            name = name,
            autoRemove = autoRemove,
            init = init
        )
    }
}