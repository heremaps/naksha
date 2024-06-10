package naksha.base

import naksha.base.PlatformMapApi.Companion.map_get
import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * The description of a property stored in a map.
 * @property klass The type of the property.
 * @property autoCreate If the value should be auto-created, when it is _null_. If additionally an [defaultValue] is
 * defined, then this value will be set, when the value is read while being _null_. Otherwise, only the [defaultValue]
 * is returned, but the underlying property stays _undefined_ or _null_.
 * @property defaultValue The default value to return, when reading the member while being _null_.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE")
@JsExport
open class NullableProperty<V : Any, MAP : P_Map<String, V>, T : V>(
    val klass: KClass<out T>,
    val autoCreate: Boolean = false,
    val defaultValue: T? = null
) {
    open operator fun getValue(self: MAP, property: KProperty<*>): T? {
        val key = property.name
        if (autoCreate) {
            if (defaultValue == null) return self.getOrCreate(key, klass)
            return self.getOrSet(key, defaultValue)
        }
        return Proxy.box(map_get(self.data(), key), klass, defaultValue)
    }

    open operator fun setValue(self: MAP, property: KProperty<*>, value: T?) = self.put(property.name, value)
}
