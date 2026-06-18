@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request

import naksha.base.*
import naksha.geo.HereTile
import naksha.model.XyzNs
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import naksha.model.request.query.*
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A set of conditions to be executed against the storage, logically AND combined.
 *
 * If a logical **OR** between the different condition is needed, for example search for features being in a certain bounding box **or** having a certain tag, then two read-requests should be executed, and joined by the client. These queries can be executed in parallel using two distinct sessions to improve the performance.
 *
 * Within each condition a logical **OR** can be applied using the corresponding wrappers, for example:
 * - [naksha.model.request.query.SpOr] - logical OR for spatial conditions
 * - [naksha.model.request.query.TagOr] - logical OR for tag conditions
 * - [naksha.model.request.query.POr] - logical OR for property conditions
 * - [naksha.model.request.query.MemberOr] - logical OR for metadata conditions
 *
 * @since 3.0
 */
@JsExport
open class RequestQuery : AnyObject() {

    companion object RequestQuery_C {
        @JvmField
        val TAGS_PROP_PATH = arrayOf(NakshaFeature.PROPERTIES_KEY, NakshaProperties.XYZ_KEY, XyzNs.TAGS_KEY)

        private val INT_LIST = NotNullProperty<RequestQuery, IntList>(IntList::class)
        private val SPATIAL_QUERY_OR_NULL = NullableProperty<RequestQuery, ISpatialQuery>(ISpatialQuery::class)
        private val TAG_QUERY_OR_NULL = NullableProperty<RequestQuery, ITagQuery>(ITagQuery::class)
        private val PROPERTIES_QUERY_OR_NULL = NullableProperty<RequestQuery, IPropertyQuery>(IPropertyQuery::class)
        private val MEMBER_QUERY_OR_NULL = NullableProperty<RequestQuery, IMemberQuery>(IMemberQuery::class)
    }

    /**
     * Search for features matching the given spatial query.
     * @since 3.0.0
     * @see ISpatialQuery
     */
    // TODO: We need to replace this with MemberQueries.
    //       Actually, in the members we can store multiple geometries, all of them can be searched.
    @Deprecated("Use member queries, there can be multiple spatial members that can be searched and combined.")
    var spatial by SPATIAL_QUERY_OR_NULL

    /**
     * Search for features matching the given tag query.
     * @since 3.0.0
     * @see ITagQuery
     */
    // TODO: We need to replace this with MemberQueries.
    //       Actually, in the members we can store multiple tag-like members, all of them can be searched.
    @Deprecated("Use member queries, there can be multiple tag-like members that can be searched and combined.")
    var tags by TAG_QUERY_OR_NULL

    /**
     * Search for features matching the given property query.
     * @since 3.0.0
     * @see IPropertyQuery
     */
    // TODO: Remove this completely, we should only allow to actually search for members.
    //       Not members must be post-filtered by the client, we can offer the helper we have for this case.
    //       This makes it as well very clear to the client and user, what can found fast, and what will be slow.
    @Deprecated("Remove this completely, we only allow to actually search for members.")
    var properties by PROPERTIES_QUERY_OR_NULL

    /**
     * Search for features matching the given member query.
     * @since 3.0.0
     * @see IMemberQuery
     */
    // TODO: Because actually everything boils down to member-queries only, we should move this into the ReadFeatures directly.
    //       We only need this property in ReadFeaturs, so that clients can defined how indices they have created are used.
    var members by MEMBER_QUERY_OR_NULL

    /**
     * Search for features that have a reference point in one of the given tiles.
     *
     * If the list is empty, no limit is applied.
     * @since 3.0.0
     */
    // TODO: Remove this completely, clients that need spatial queries should use spatial members.
    @Deprecated("Please use spatial members instead")
    var refTiles by INT_LIST

    /**
     * Adds the given tile to the list of tiles to query for reference points, so updating [refTiles].
     * @param tile the tile to search in.
     * @return this.
     * @since 3.0.0
     */
    @Deprecated("Please use spatial members instead")
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
    @Deprecated("Please use spatial members instead")
    fun removeRefTile(tile: HereTile): RequestQuery {
        refTiles.remove(tile.intKey)
        return this
    }

    /**
     * Checks whether this query is effectively empty (it has no actual conditions).
     * @return _true_ if there are no conditions to be used; _false_ if a `WHERE` must be generated.
     */
    fun hasNoConditions(): Boolean {
        return refTiles.isEmpty()
                && spatial == null
                && tags == null
                && properties == null
                && members == null
    }
}