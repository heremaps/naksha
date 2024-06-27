package naksha.geo.cords

import naksha.base.AbstractListProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.reflect.KClass

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class CoordinatesProxy<E : Any>(elementKlass: KClass<out E>) : AbstractListProxy<E>(elementKlass) {

}