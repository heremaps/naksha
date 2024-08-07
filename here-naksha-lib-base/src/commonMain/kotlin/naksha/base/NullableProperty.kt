package naksha.base

import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * The description of a property stored in a map.
 *
 * It is recommended to add this delegator statically to avoid that for every object instance a new instance of the delegator is created.
 * The Kotlin compiler will even inline the getter/setter calls in that case:
 * ```kotlin
 * class Foo : ObjectProxy() {
 *   companion object {
 *     private val STRING_NULL = NullableProperty<Foo, String>
 *                        (String::class)
 *     private val INT = NotNullProperty<Foo, Int>
 *                        (Int::class) { _,_ -> 0 }
 *   }
 *   var firstName: String? by STRING_NULL
 *   var lastName: String? by STRING_NULL
 *   var age: Int by INT
 *   var other: Int by INT
 * }
 * ```
 * @param OBJECT_TYPE the type of the object to which to attach the property.
 * @param PROPERTY_TYPE the type of the property, must have the base type of the map as super type.
 * @property klass the [KClass] of the property type.
 * @property autoCreate if the value should be auto-created, when it is _null_. If additionally an [init] is defined, then this invoked
 * before auto-generating a value.
 * @property name the name of the property in the map, if different from the property name, if _null_, the property name is used.
 * @property init the initializer to create a new value, when the property does not exist or the value is not of the desired type. If the
 * initializer returns _null_, the value is created by invoking the default constructor of the value type.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE")
@JsExport
open class NullableProperty<OBJECT_TYPE : AnyObject, PROPERTY_TYPE : Any>(
    klass: KClass<out PROPERTY_TYPE>,
    autoCreate: Boolean = false,
    name: String? = null,
    init: ((self: OBJECT_TYPE, name: String) -> PROPERTY_TYPE?)? = null
) : NullableMapProperty<OBJECT_TYPE, Any, PROPERTY_TYPE>(klass, autoCreate, name, init)
