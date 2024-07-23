@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import naksha.geo.HereTile
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Read all features that have their reference point in the given tile.
 *
 * Examples:
 * ```Kotlin
 * SpRefInHereTile(HereTile(0,0))
 * ```
 * ```Java
 * new SpRefInHereTile(new HereTile(0,0,15))
 * ```
 *
 * @property tile the [HereTile] in which to look for features.
 */
@JsExport
class SpRefInHereTile(@JvmField var tile: HereTile) : SpatialOuery
