@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface for spatial queries.
 * @see SpAnd
 * @see SpOr
 * @see SpNot
 * @see SpIntersects
 * @see SpRefInHereTile
 */
@JsExport
interface ISpatialQuery : IQuery