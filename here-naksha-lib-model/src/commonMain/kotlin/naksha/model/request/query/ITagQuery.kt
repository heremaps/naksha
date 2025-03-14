@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface to all tag queries.
 * @see TagOr
 * @see TagAnd
 * @see TagNot
 * @see TagQuery
 */
@JsExport
interface ITagQuery : IQuery