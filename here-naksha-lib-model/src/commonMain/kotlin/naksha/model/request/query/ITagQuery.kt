@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface to all tag queries.
 *
 * @since 3.0
 * @see IQuery
 * @see ITagQuery
 * @see TagQuery
 * @see TagOr
 * @see TagAnd
 * @see TagNot
 */
@JsExport
interface ITagQuery : IQuery