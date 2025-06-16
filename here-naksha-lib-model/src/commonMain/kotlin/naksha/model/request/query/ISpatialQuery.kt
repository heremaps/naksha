@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface for spatial queries.
 * @see IQuery
 * @see ISpatialQuery
 * @see SpIntersects
 * @see SpRefInHereTile
 * @see SpAnd
 * @see SpOr
 * @see SpNot
 */
@JsExport
interface ISpatialQuery : IQuery
