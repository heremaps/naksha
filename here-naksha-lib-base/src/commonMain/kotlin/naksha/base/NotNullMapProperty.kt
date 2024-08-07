package naksha.base

import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * The description of a property stored in a map.
 *
 * It is recommended to add this delegator statically to avoid that for every object instance a new instance of the delegator is created.
 * The Kotlin compiler will even inline the getter/setter calls in that case:
 * ```kotlin
 * class Foo : MapProxy<String, Any>(String::class, Any::class) {
 *   companion object {
 *     private val STRING_NULL = NullableMapProperty<Foo, Any, String>
 *                        (String::class)
 *     private val INT = NotNullMapProperty<Foo, Any, Int>
 *                        (Int::class) { _,_ -> 0 }
 *   }
 *   var firstName: String? by STRING_NULL
 *   var lastName: String? by STRING_NULL
 *   var age: Int by INT
 *   var other: Int by INT
 * }
 * ```
 * @param MAP the type of the map to which to attach the property.
 * @param MAP_VALUE_TYPE the base type of all values in the map.
 * @param PROPERTY_TYPE the type of the property, must have the base type of the map as super type.
 * @property klass the [KClass] of the property type.
 * @property name the name of the property in the map, if different from the property name, if _null_, the property name is used.
 * @property init the initializer to create a new value, when the property does not exist or the value is not of the desired type. If the
 * initializer returns _null_, the value is created by invoking the default constructor of the value type.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE")
@JsExport
open class NotNullMapProperty<MAP : MapProxy<String, MAP_VALUE_TYPE>, MAP_VALUE_TYPE : Any, PROPERTY_TYPE : MAP_VALUE_TYPE>(
    val klass: KClass<out PROPERTY_TYPE>,
    val name: String? = null,
    val init: ((self: MAP, name: String) -> PROPERTY_TYPE?)? = null
) {
    open operator fun getValue(self: MAP, property: KProperty<*>): PROPERTY_TYPE = self.getOrCreate(this.name ?: property.name, klass, init)

    open operator fun setValue(self: MAP, property: KProperty<*>, value: PROPERTY_TYPE) = self.put(this.name ?: property.name, value)
}
