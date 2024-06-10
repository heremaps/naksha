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
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE", "UNCHECKED_CAST")
@JsExport
open class NullableProperty<V>(
    val klass: KClass<*>,
    val autoCreate: Boolean = false,
    val defaultValue: V? = null
) {
    open operator fun getValue(self: P_Map<String, Any>, property: KProperty<*>): V? {
        val key = property.name
        if (autoCreate) {
            if (defaultValue == null) return self.getOrCreate(key, klass) as V
            return self.getOrSet(key, defaultValue)
        }
        return Proxy.box(map_get(self.data(), key), klass, defaultValue) as V
    }

    open operator fun setValue(self: P_Map<String, Any>, property: KProperty<*>, value: V?) = self.put(property.name, value)
}
