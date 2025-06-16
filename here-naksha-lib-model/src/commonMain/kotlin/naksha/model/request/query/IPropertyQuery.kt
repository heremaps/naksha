@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface for property queries.
 * @since 3.0
 * @see IQuery
 * @see IPropertyQuery
 * @see PQuery
 * @see PAnd
 * @see POr
 * @see PNot
 */
@JsExport
interface IPropertyQuery : IQuery
