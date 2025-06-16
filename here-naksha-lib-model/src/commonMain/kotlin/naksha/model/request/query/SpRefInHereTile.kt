@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.Int_TYPE
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.geo.HereTile
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Read all features that have their reference point in the given tile.
 * @since 3.0
 * @see IQuery
 * @see ISpatialQuery
 * @see SpRefInHereTile
 */
@JsExport
open class SpRefInHereTile() : AnyObject(), ISpatialQuery {

    /**
     * Create an initialized tile query.
     *
     * Examples:
     * ```Kotlin
     * SpRefInHereTile(HereTile(0,0).intKey)
     * ```
     * ```Java
     * new SpRefInHereTile(new HereTile(0,0,15).intKey)
     * ```
     * @param tile the binary HERE tile ID, can be wrapped int [HereTile] to perform operations with it.
     */
    @JsName("of")
    constructor(tile: Int) : this() {
        this.tile = tile
    }

    /**
     * Create an initialized tile query.
     *
     * Examples:
     * ```Kotlin
     * SpRefInHereTile(HereTile(0,0))
     * ```
     * ```Java
     * new SpRefInHereTile(new HereTile(0,0,15))
     * ```
     * @param tile the HERE tile.
     */
    @JsName("ofHereTile")
    constructor(tile: HereTile) : this() {
        this.tile = tile.intKey
    }

    companion object SpRefInHereTile_C {
        /**
         * The [PlatformType] of [SpRefInHereTile].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpRefInHereTile::class).withPackageName(PACKAGE_NAME)

        private val INT = NotNullProperty<SpRefInHereTile, Int>(Int_TYPE) { _, _ -> 0 }
    }

    /**
     * The binary HERE tile ID, can be wrapped int [HereTile] to perform operations with it.
     */
    var tile by INT

    private var cached: HereTile? = null

    /**
     * Returns the [tile] as [HereTile], caches the value so that calling of the function multiple times in a row always return the same result.
     * @return the [tile] as [HereTile] (cached).
     */
    fun getHereTile() : HereTile {
        val key = tile
        var tile = cached
        if (tile != null && key == tile.intKey) return tile
        tile = HereTile(key)
        cached = tile
        return tile
    }
}