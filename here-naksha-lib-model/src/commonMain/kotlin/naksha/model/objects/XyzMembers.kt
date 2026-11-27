@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.model.objects

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * All members being part of the classic XYZ-Hub architecture, plus further extensions added later in Data-Hub and Naksha v1, v2. All internal administrative object are stored in this format.
 * @since 3.0
 */
@JsExport
class XyzMembers private constructor() {

    companion object XyzMembers_C {

        val XYZ_JSON_PATH = JsonPath("properties", "@ns:com:here:xyz")

        // -------------------------------------------------------------------------
        // Mandatory members — storage-managed, always present.
        // -------------------------------------------------------------------------

        /**
         * The same as [StandardMembers.Tn], but with a Data-Hub compatible path.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzTn = TupleNumberMember(StandardMembers.Tn, JsonPath("properties", "@ns:com:here:xyz", "uuid"))

        /**
         * The same as [StandardMembers.NextVersion], but with a Data-Hub compatible path.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzNextVersion = Int64Member(StandardMembers.NextVersion, JsonPath("properties", "@ns:com:here:xyz", "nextVersion"))

        /**
         * `gfn` — `@.properties.@ns:com:here:xyz.globalBookFn` — The same as [StandardMembers.GlobalBookFeatureNumber], but with a Data-Hub compatible path.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzGlobalBookFeatureNumber = Int64Member(StandardMembers.GlobalBookFeatureNumber, JsonPath("properties", "@ns:com:here:xyz", "globalBookFn"))

        /**
         * `feature` — `@` — The same as [StandardMembers.FeatureBytes].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzFeatureBytes = ByteArrayMember(StandardMembers.FeatureBytes, JsonPath()).withMandatory().withVirtual()

        /**
         * `id` — `@.id` — The same as [StandardMembers.FeatureBytes].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzId = StringMember(StandardMembers.Id, JsonPath("id"))

        // -------------------------------------------------------------------------
        // Optional members.
        // -------------------------------------------------------------------------

        /**
         * `geo` — `@.geometry` — feature geometry stored as TWKB. `null` if the feature has no geometry.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzGeometry = SpatialMember(StandardMembers.Geometry, JsonPath("geometry"))

        /**
         * `updated_at` — `@.properties.@ns:com:here:xyz.updatedAt` — millisecond epoch timestamp of the last modification. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzUpdatedAt = Int64Member("updated_at", JsonPath("properties", "@ns:com:here:xyz", "updatedAt"))

        /**
         * `created_at` — `@.properties.@ns:com:here:xyz.createdAt` — millisecond epoch timestamp of the initial creation. `null` means the
         * timestamp equals [XyzUpdatedAt] (first-write optimisation). Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCreatedAt = Int64Member("created_at", JsonPath("properties", "@ns:com:here:xyz", "createdAt"))

        /**
         * `author_ts` — `@.properties.@ns:com:here:xyz.authorTs` — millisecond epoch timestamp of the last author change. `null` means the
         * timestamp equals [XyzUpdatedAt]. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAuthorTimestamp = Int64Member("author_ts", JsonPath("properties", "@ns:com:here:xyz", "authorTs"))

        /**
         * `hash` — `@.properties.@ns:com:here:xyz.hash` — content hash of the tuple, computed by the storage. `null` if not recorded.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzHash = Int32Member("hash", JsonPath("properties", "@ns:com:here:xyz", "hash"))

        /**
         * `here_tile` — `@.properties.@ns:com:here:xyz.hereTile` — HERE tile key (binary) of the reference point. `null` if not known.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzHereTile = Int32Member("here_tile", JsonPath("properties", "@ns:com:here:xyz", "hereTile"))

        /**
         * `cc` — `@.properties.@ns:com:here:xyz.changeCount` — change-count: how many times this feature has been modified. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzChangeCount = Int32Member(StandardMembers.ChangeCount, JsonPath("properties", "@ns:com:here:xyz", "changeCount"))

        /**
         * `base_tn` — `@.properties.@ns:com:here:xyz.base` — base tuple-number (`BYTE_ARRAY`), set when a three-way merge was performed.
         * `null` otherwise. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        //TODO we might consider this to be TupleNumberMember when 3 way merge is supported,
        // and we want to left out duplicated data like db, catalog, collection,
        // and that needs special handling similar to the default tn
        val XyzBaseTn = ByteArrayMember("base_tn", JsonPath("properties", "@ns:com:here:xyz", "base"))

        /**
         * `app_id` — `@.properties.@ns:com:here:xyz.appId` — identifier of the application that wrote this tuple. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAppId = StringMember("app_id", JsonPath("properties", "@ns:com:here:xyz", "appId"))

        /**
         * `author` — `@.properties.@ns:com:here:xyz.author` — identifier of the human author that takes ownership for this tuple. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAuthor = StringMember("author", JsonPath("properties", "@ns:com:here:xyz", "author"))

        /**
         * `origin` — `@.properties.@ns:com:here:xyz.origin` — stringified reference to the originating feature when this feature was forked or
         * copied from another storage, map, or collection. Used for rebase support. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzOrigin = StringMember(StandardMembers.Origin, JsonPath("properties", "@ns:com:here:xyz", "origin"))

        /**
         * `target` — `@.properties.@ns:com:here:xyz.target` — stringified reference to the feature into which this feature was joined.
         * Set when multiple features are merged into one. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzTarget = StringMember("target", JsonPath("properties", "@ns:com:here:xyz", "target"))

        /**
         * `ft` — `@.properties.featureType` — feature-type string. `null` when it matches the collection's
         * [default feature type][NakshaCollection.defaultFeatureType], avoiding redundant storage.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzFeatureType = StringMember("ft", JsonPath("properties", "featureType"))

        /**
         * `cv0` — `@.properties.@ns:com:here:xyz.cv0` — custom numeric value 0 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue0 = Float64Member("cv0", JsonPath("properties", "@ns:com:here:xyz", "cv0"))

        /**
         * `cv1` — `@.properties.@ns:com:here:xyz.cv1` — custom numeric value 1 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue1 = Float64Member("cv1", JsonPath("properties", "@ns:com:here:xyz", "cv1"))

        /**
         * `cv2` — `@.properties.@ns:com:here:xyz.cv2` — custom numeric value 2 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue2 = Float64Member("cv2", JsonPath("properties", "@ns:com:here:xyz", "cv2"))

        /**
         * `cv3` — `@.properties.@ns:com:here:xyz.cv3` — custom numeric value 3 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue3 = Float64Member("cv3", JsonPath("properties", "@ns:com:here:xyz", "cv3"))

        /**
         * `cs0` — `@.properties.@ns:com:here:xyz.cs0` — custom string value 0. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString0 = StringMember("cs0", JsonPath("properties", "@ns:com:here:xyz", "cs0"))

        /**
         * `cs1` — `@.properties.@ns:com:here:xyz.cs1` — custom string value 1. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString1 = StringMember("cs1", JsonPath("properties", "@ns:com:here:xyz", "cs1"))

        /**
         * `cs2` — `@.properties.@ns:com:here:xyz.cs2` — custom string value 2. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString2 = StringMember("cs2", JsonPath("properties", "@ns:com:here:xyz", "cs2"))

        /**
         * `cs3` — `@.properties.@ns:com:here:xyz.cs3` — custom string value 3. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString3 = StringMember("cs3", JsonPath("properties", "@ns:com:here:xyz", "cs3"))

        /**
         * `tags` — `@.properties.@ns:com:here:xyz.tags` — feature tags, the classic XYZ tags array, e.g. `["foo", "bar"]`, stored as a
         * [tag_list][MemberType.TAG_LIST] of unique strings. The list is persisted unmodified, so the element order is preserved when reading the feature back. `null` if the feature has no tags.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzTags = TagListMember("tags", JsonPath("properties", "@ns:com:here:xyz", "tags"))

        /**
         * `ref_point` — `@.referencePoint` — geometry reference point (always a single point), stored as TWKB. Used to compute the [XyzHereTile] value. `null` if the feature has no explicit reference point.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzReferencePoint = SpatialMember("ref_point", JsonPath("referencePoint"))

        /**
         * All members of XYZ compatible features.
         * @since 3.0
         */
        @JvmField @JsStatic
        val ALL: List<Member> = listOf(
            XyzTn, XyzNextVersion, XyzGlobalBookFeatureNumber, XyzFeatureBytes, XyzId, XyzGeometry,
            // Optional members
            XyzUpdatedAt, XyzCreatedAt, XyzAuthorTimestamp,
            XyzHash, XyzHereTile, XyzChangeCount, XyzBaseTn,
            XyzAppId, XyzAuthor, XyzOrigin, XyzTarget, XyzFeatureType,
            XyzCustomValue0, XyzCustomValue1, XyzCustomValue2, XyzCustomValue3,
            XyzCustomString0, XyzCustomString1, XyzCustomString2, XyzCustomString3,
            XyzTags, XyzReferencePoint
        )
    }
}