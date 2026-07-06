@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface for property queries.
 * @see PAnd
 * @see POr
 * @see PNot
 * @see PQuery
 */
@JsExport
@Deprecated("Replaced with op", replaceWith = ReplaceWith("Op"))
interface IPropertyQuery : IQuery
