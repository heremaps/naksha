@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport

/**
 * Spatial query.
 *
 * @see SpIntersects
 * @see SpRefInHereTile
 */
@JsExport
interface SpatialOuery : IQuery<SpatialOuery>
