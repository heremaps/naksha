@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * A lambda to query the default symbol to which to bind a given [KClass] to.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
interface SymbolResolver {
    /**
     * A method called to return the symbol to which to bind the given [KClass].
     * @param klass The [KClass] for which to return the default symbol.
     * @return The default symbol or _null_, if this resolver is not responsible for the given [KClass].
     */
    fun call(klass: KClass<*>): Symbol?
}