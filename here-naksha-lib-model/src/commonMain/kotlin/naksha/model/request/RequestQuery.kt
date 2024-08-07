@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request

import naksha.base.*
import naksha.geo.HereTile
import naksha.model.request.query.*
import kotlin.js.JsExport

/**
 * A set of conditions to be executed against the storage, logically AND combined.
 *
 * If a logical **OR** between the different condition is needed, for example search for features being in a certain bounding box **or** having a certain tag, then two read-requests should be executed, and joined by the client. These queries can be executed in parallel using two distinct sessions to improve the performance.
 *
 * Within each condition a logical **OR** can be applied using the corresponding wrappers, for example:
 * - [naksha.model.request.query.SpOr] - logical OR for spatial conditions
 * - [naksha.model.request.query.TagOr] - logical OR for tag conditions
 * - [naksha.model.request.query.POr] - logical OR for property conditions
 *
 * @since 3.0.0
 */
@JsExport
open class RequestQuery : AnyObject() {

    companion object RequestQuery_C {
        private val INT_LIST = NotNullProperty<RequestQuery, IntList>(IntList::class)
        private val SPATIAL_QUERY_NULL = NullableProperty<RequestQuery, ISpatialQuery>(ISpatialQuery::class)
        private val TAG_QUERY_NULL = NullableProperty<RequestQuery, ITagQuery>(ITagQuery::class)
        private val PROPERTIES_QUERY_NULL = NullableProperty<RequestQuery, IPropertyQuery>(IPropertyQuery::class)
    }

    /**
     * Search for features that match the given spatial query.
     *
     * This condition excludes features
     * @since 3.0.0
     */
    var spatial by SPATIAL_QUERY_NULL

    /**
     * Search for features that match the given tag query.
     * @since 3.0.0
     */
    var tags by TAG_QUERY_NULL

    /**
     * Search for features match the given property query.
     * @since 3.0.0
     */
    var properties by PROPERTIES_QUERY_NULL

    /**
     * Search for features that have a reference point in one of the given tiles.
     *
     * If the list is empty, no limit is applied.
     * @since 3.0.0
     */
    var refTiles by INT_LIST

    /**
     * Adds the given tile to the list of tiles to query for reference points, so updating [refTiles].
     * @param tile the tile to search in.
     * @return this.
     * @since 3.0.0
     */
    fun addRefTile(tile: HereTile): RequestQuery {
        refTiles.add(tile.intKey)
        return this
    }

    /**
     * Removes the given tile from the list of tiles to query for reference points, so updating [refTiles].
     * @param tile the tile no longer search in.
     * @return this.
     * @since 3.0.0
     */
    fun removeRefTile(tile: HereTile): RequestQuery {
        refTiles.remove(tile.intKey)
        return this
    }
}