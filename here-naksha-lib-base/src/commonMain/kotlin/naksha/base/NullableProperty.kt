package naksha.base

import naksha.base.PlatformMapApi.Companion.map_contains_key
import naksha.base.PlatformMapApi.Companion.map_get
import naksha.base.PlatformMapApi.Companion.map_set
import naksha.base.Proxy.Companion.box
import naksha.base.Proxy.Companion.unbox
import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * The description of a property stored in a map.
 *
 * It is recommended to add this delegator statically to avoid that for every object instance a new instance of the delegator is created.
 * The Kotlin compiler will even inline the getter/setter calls in that case:
 * ```kotlin
 * class FooProxy : ObjectProxy() {
 *   companion object {
 *     private val NAME = NullableProperty<Any, Foo, String>
 *                        (String::class)
 *     private val AGE = NotNullProperty<Any, Foo, Int>
 *                        (Int::class) { _,_ -> 0 }
 *   }
 *   var name: String? by NAME
 *   var age: Int by AGE
 * }
 * ```
 * @param MAP_VALUE_TYPE The type of the map value.
 * @param MAP The type of the map to which to attach the property.
 * @param PROPERTY_TYPE The type of the property, must have [MAP_VALUE_TYPE] as super type.
 * @property klass the type of the property.
 * @property autoCreate tf the value should be auto-created, when it is _null_. If additionally an [init] is defined, then this invoked
 * before auto-generating a value.
 * @property name the name of the property in the underlying map, if different from the property name.
 * @property init the initializer to create a new value, when the property does not exist or the value is not of the desired type. If the
 * initializer returns _null_, the value is created by invoking the default constructor of the value type.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE")
@JsExport
open class NullableProperty<MAP_VALUE_TYPE : Any, MAP : AbstractMapProxy<String, MAP_VALUE_TYPE>, PROPERTY_TYPE : MAP_VALUE_TYPE>(
    val klass: KClass<out PROPERTY_TYPE>,
    val autoCreate: Boolean = false,
    val name: String? = null,
    val init: ((self: MAP, name: String) -> PROPERTY_TYPE?)? = null
) {
    open operator fun getValue(self: MAP, property: KProperty<*>): PROPERTY_TYPE? {
        val key = this.name ?: property.name
        if (autoCreate) return self.getOrCreate(key, klass, init)
        val data = self.platformObject()
        var value: PROPERTY_TYPE? = null
        if (map_contains_key(data, key)) {
            val raw = map_get(data, key)
            value = box(raw, klass)
        }
        if (value == null && init != null) {
            value = init.invoke(self, key)
            map_set(data, key, unbox(value))
        }
        return value
    }

    open operator fun setValue(self: MAP, property: KProperty<*>, value: PROPERTY_TYPE?) = self.put(this.name ?: property.name, value)
}
