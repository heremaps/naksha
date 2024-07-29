package naksha.base

import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_contains_key
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_set
import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

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
 * class Foo : MapProxy<String, Any>(String::class, Any::class) {
 *   companion object {
 *     private val FOO_NULL = NullableMapEnum<Foo, Any, FooEnum>
 *                        (FooEnum::class)
 *     private val FOO = NotNullMapEnum<Foo, Any, FooEnum>
 *                        (FooEnum::class)
 *   }
 *   var foo1: FooEnum? by FOO_NULL
 *   var foo2: FooEnum by FOO
 * }
 * ```
 * @param MAP the type of the map to which to attach the property.
 * @param MAP_VALUE_TYPE the base type of all values in the map.
 * @param PROPERTY_TYPE the type of the property, must have the base type of the map as super type.
 * @property klass the [KClass] of the property type.
 * @property name the name of the property in the map, if different from the property name, if _null_, the property name is used.
 * @property init the initializer to create an initial value, when the property does not exist or is of an invalid type.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE")
@JsExport
open class NullableMapEnum<MAP : MapProxy<String, MAP_VALUE_TYPE>, MAP_VALUE_TYPE : Any, PROPERTY_TYPE : JsEnum>(
    val klass: KClass<out PROPERTY_TYPE>,
    val name: String? = null,
    val init: ((self: MAP, name: String) -> PROPERTY_TYPE?)? = null
) {
    open operator fun getValue(self: MAP, property: KProperty<*>): PROPERTY_TYPE? {
        val key = this.name ?: property.name
        val po = self.platformObject()
        var raw: Any? = null
        if (map_contains_key(po, key)) {
            raw = map_get(po, key)
        } else {
            val init = this.init
            if (init != null) {
                val e = init(self, key)
                if (e != null) {
                    map_set(po, key, e.value)
                    return e
                }
            }
        }
        return JsEnum.getDefined(raw, klass)
    }

    open operator fun setValue(self: MAP, property: KProperty<*>, value: PROPERTY_TYPE?) {
        val key = this.name ?: property.name
        val po = self.platformObject()
        map_set(po, key, value?.value)
    }
}
