@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * A lambda to query the default symbol to link a given value [type][PlatformType].
 *
 * Creating _symbol resolvers_ allows to link certain [types][PlatformType] into specific namespaces, a namespace is represented by a [Symbol].
 * @see Symbols
 * @see Symbol
 * @see SymbolMember
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
interface SymbolResolver {
    /**
     * A method called to return the [symbol][Symbol] to which values of the given [type][PlatformType] are linked by default.
     * @param type The [PlatformType] of the value for which to return the default [Symbol].
     * @return The default [Symbol] or `null`, if this resolver is not responsible for the given [PlatformType].
     */
    fun call(type: PlatformType<*>): Symbol?
}