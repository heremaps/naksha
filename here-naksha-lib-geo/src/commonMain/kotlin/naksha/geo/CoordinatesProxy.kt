package naksha.geo

import naksha.base.AbstractListProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.reflect.KClass

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class CoordinatesProxy<T : Any>(elementKlass: KClass<out T>) : AbstractListProxy<T>(elementKlass) {

}