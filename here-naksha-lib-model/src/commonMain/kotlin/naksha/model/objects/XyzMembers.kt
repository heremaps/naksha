@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.model.objects

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * All members being part of the classic XYZ-Hub architecture, plus further extensions added later in Data-Hub and Naksha v1, v2. All internal adminstrative object are stored in this format.
 * @since 3.0
 */
@JsExport
class XyzMembers private constructor() {
    companion object XyzMembers_C {
        // -------------------------------------------------------------------------
        // Mandatory members — storage-managed, always present.
        // -------------------------------------------------------------------------

        /**
         * The same as [StandardMembers.Tn], but with a Data-Hub compatible path.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzTn = Member(StandardMembers.Tn, JsonPath("properties", "@ns:com:here:xyz", "uuid"))

        /**
         * The same as [StandardMembers.NextVersion], but with a Data-Hub compatible path.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzNextVersion = Member(StandardMembers.NextVersion, JsonPath("properties", "@ns:com:here:xyz", "nextVersion"))

        /**
         * The same as [StandardMembers.GlobalBookFeatureNumber], but with a Data-Hub compatible path.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzGlobalBookFeatureNumber = Member(StandardMembers.GlobalBookFeatureNumber, JsonPath("properties", "@ns:com:here:xyz", "globalBookFn"))

        /**
         * The same as [StandardMembers.Feature].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzFeature = Member(StandardMembers.Feature, JsonPath())

        /**
         * The same as [StandardMembers.Feature].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzId = Member(StandardMembers.Id, JsonPath("id"))

        // -------------------------------------------------------------------------
        // Optional members.
        // -------------------------------------------------------------------------

        /**
         * `geo` — feature geometry stored as TWKB. `null` if the feature has no geometry.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzGeometry = Member("geo", MemberType.SPATIAL, JsonPath("geometry"))

        /**
         * `updated_at` — millisecond epoch timestamp of the last modification. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzUpdatedAt = Member("updated_at", MemberType.INT64, JsonPath("properties", "@ns:com:here:xyz", "updatedAt"))

        /**
         * `created_at` — millisecond epoch timestamp of the initial creation. `null` means the
         * timestamp equals [XyzUpdatedAt] (first-write optimisation). Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCreatedAt = Member("created_at", MemberType.INT64, JsonPath("properties", "@ns:com:here:xyz", "createdAt"))

        /**
         * `author_ts` — millisecond epoch timestamp of the last author change. `null` means the
         * timestamp equals [XyzUpdatedAt]. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAuthorTimestamp = Member("author_ts", MemberType.INT64, JsonPath("properties", "@ns:com:here:xyz", "authorTs"))

        /**
         * `hash` — content hash of the tuple, computed by the storage. `null` if not recorded.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzHash = Member("hash", MemberType.INT32, JsonPath("properties", "@ns:com:here:xyz", "hash"))

        /**
         * `here_tile` — HERE tile key (binary) of the reference point. `null` if not known.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzHereTile = Member("here_tile", MemberType.INT32, JsonPath("properties", "@ns:com:here:xyz", "hereTile"))

        /**
         * `cc` — change-count: how many times this feature has been modified. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzChangeCount = Member("cc", MemberType.INT32, JsonPath("properties", "@ns:com:here:xyz", "changeCount"))

        /**
         * `base_tn` — base tuple-number (`BYTE_ARRAY`), set when a three-way merge was performed.
         * `null` otherwise. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzBaseTn = Member("base_tn", MemberType.BYTE_ARRAY, JsonPath("properties", "@ns:com:here:xyz", "base"))

        /**
         * `app_id` — identifier of the application that wrote this tuple. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAppId = Member("app_id", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "appId"))

        /**
         * `author` — identifier of the human author that takes ownership for this tuple. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAuthor = Member("author", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "author"))

        /**
         * `origin` — stringified reference to the originating feature when this feature was forked or
         * copied from another storage, map, or collection. Used for rebase support. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzOrigin = Member("origin", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "origin"))

        /**
         * `target` — stringified reference to the feature into which this feature was joined.
         * Set when multiple features are merged into one. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzTarget = Member("target", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "target"))

        /**
         * `ft` — feature-type string. `null` when it matches the collection's
         * [default feature type][NakshaCollection.defaultFeatureType], avoiding redundant storage.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzFeatureType = Member("ft", MemberType.STRING, JsonPath("properties", "featureType"))

        /**
         * `cv0` — custom numeric value 0 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue0 = Member("cv0", MemberType.FLOAT64, JsonPath("properties", "@ns:com:here:xyz", "cv0"))

        /**
         * `cv1` — custom numeric value 1 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue1 = Member("cv1", MemberType.FLOAT64, JsonPath("properties", "@ns:com:here:xyz", "cv1"))

        /**
         * `cv2` — custom numeric value 2 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue2 = Member("cv2", MemberType.FLOAT64, JsonPath("properties", "@ns:com:here:xyz", "cv2"))

        /**
         * `cv3` — custom numeric value 3 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue3 = Member("cv3", MemberType.FLOAT64, JsonPath("properties", "@ns:com:here:xyz", "cv3"))

        /**
         * `cs0` — custom string value 0. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString0 = Member("cs0", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "cs0"))

        /**
         * `cs1` — custom string value 1. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString1 = Member("cs1", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "cs1"))

        /**
         * `cs2` — custom string value 2. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString2 = Member("cs2", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "cs2"))

        /**
         * `cs3` — custom string value 3. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString3 = Member("cs3", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "cs3"))

        /**
         * `tags` — feature tags, the classic XYZ tags array located at
         * `properties -> @ns:com:here:xyz -> tags` (e.g. `["foo", "bar"]`), stored as a
         * [set][MemberType.SET] of unique strings. The array is persisted unmodified, so the
         * element order is preserved when reading the feature back. `null` if the feature has no
         * tags. Supports element containment queries via [IndexType.SET]. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzTags = Member("tags", MemberType.SET, JsonPath("properties", "@ns:com:here:xyz", "tags"))

        /**
         * `ref_point` — geometry reference point (always a single point), stored as TWKB. Used to compute the [XyzHereTile] value. `null` if the feature has no explicit reference point.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzReferencePoint = Member("ref_point", MemberType.SPATIAL, JsonPath("referencePoint"))

        /**
         * All members of XYZ compatible features.
         * @since 3.0
         */
        @JvmField @JsStatic
        val ALL: List<Member> = listOf(
            XyzTn, XyzNextVersion, XyzGlobalBookFeatureNumber, XyzFeature, XyzId, XyzGeometry,
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