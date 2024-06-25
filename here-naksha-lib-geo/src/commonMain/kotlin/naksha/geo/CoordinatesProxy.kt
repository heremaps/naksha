package naksha.geo

import naksha.base.P_List
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.reflect.KClass

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class CoordinatesProxy<T : Any>(elementKlass: KClass<out T>) : P_List<T>(elementKlass) {

}