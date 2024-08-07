package naksha.base

import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * The description of a property stored in a map.
 *
 * It is recommended to add this delegator statically to avoid that for every object instance a new instance of the delegator is created.
 * The Kotlin compiler will even inline the getter/setter calls in that case:
 * ```kotlin
 * @JsExport
 * class FooEnum : JsEnum() {
 *   companion object {
 *     @JvmStatic
 *     @JsStatic
 *     val TEST = def(FooEnum::class, "test")
 *   }
 *   override fun namespace() = FooEnum::class
 *   override fun initClass() {}
 * }
 * class Foo : ObjectProxy() {
 *   companion object {
 *     private val FOO_NULL =
 *       NullableEnum<Foo, FooEnum>(FooEnum::class)
 *     private val FOO =
 *       NotNullEnum<Foo, FooEnum>(FooEnum::class)
 *   }
 *   var foo1: FooEnum? by FOO_NULL
 *   var foo2: FooEnum by FOO
 * }
 * ```
 * @param OBJECT_TYPE the type of the object to which to attach the property.
 * @param PROPERTY_TYPE the type of the property, must have the base type of the map as super type.
 * @param klass the [KClass] of the property type.
 * @param name the name of the property in the map, if different from the property name, if _null_, the property name is used.
 * @param init the initializer to create an initial value, when the property does not exist or is of an invalid type.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE")
@JsExport
open class NullableEnum<OBJECT_TYPE : AnyObject, PROPERTY_TYPE : JsEnum>(
    klass: KClass<out PROPERTY_TYPE>,
    name: String? = null,
    init: ((self: OBJECT_TYPE, name: String) -> PROPERTY_TYPE?)? = null
) : NullableMapEnum<OBJECT_TYPE, Any, PROPERTY_TYPE>(klass, name, init)
