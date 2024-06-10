package naksha.base

import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * The description of a property stored in a map.
 * @property klass The type of the property.
 * @property defaultValue The default value to return, when reading the member while being _null_.
 */
@Suppress("NON_EXPORTABLE_TYPE", "OPT_IN_USAGE")
@JsExport
open class NotNullProperty<V : Any, MAP : P_Map<String, V>, T : V>(
    val klass: KClass<out T>,
    val defaultValue: T? = null
) {
    open operator fun getValue(self: MAP, property: KProperty<*>): T {
        val key = property.name
        if (defaultValue == null) return self.getOrCreate(key, klass)
        return self.getOrSet(key, defaultValue)
    }

    open operator fun setValue(self: MAP, property: KProperty<*>, value: T) = self.put(property.name, value)
}
