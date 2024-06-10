package naksha.base

import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * The description of a property stored in a map.
 * @property klass The type of the property.
 * @property defaultValue The default value to return, when reading the member while being _null_.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE", "UNCHECKED_CAST")
@JsExport
open class NotNullProperty<V>(
    val klass: KClass<*>,
    val defaultValue: V? = null
) {
    open operator fun getValue(self: P_Map<String, Any>, property: KProperty<*>): V {
        val key = property.name
        if (defaultValue == null) return self.getOrCreate(key, klass) as V
        return self.getOrSet(key, defaultValue)
    }

    open operator fun setValue(self: P_Map<String, Any>, property: KProperty<*>, value: V) = self.put(property.name, value)
}
