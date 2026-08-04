@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The default indices created for a Data-Hub (XYZ) compatible collection.
 *
 * This is the index counterpart of [XyzMembers]: It indexes the members declared there _(e.g. [XyzTags][XyzMembers.XyzTags], [XyzAppId][XyzMembers.XyzAppId], [XyzHereTile][XyzMembers.XyzHereTile])_ and is applied via [NakshaCollection.withXyzIndices].
 *
 * An index refers to a member by its identity _(name)_, **not** by JSON path. Therefore, the name of a member is very significant.
 * @since 3.0
 */
@JsExport
class XyzIndices private constructor() {

    companion object XyzIndices_C {

        /**
         * `here_tile` — index on `here_tile`, `_fn`, `_version` (WHERE `here_tile IS NOT NULL`).
         * See [XyzMembers.XyzHereTile].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzHereTile = Index("here_tile", XyzMembers.XyzHereTile.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `app_id` — index on `app_id`, `updated_at`, `_fn`, `_version` (WHERE `app_id IS NOT NULL`).
         * See [XyzMembers.XyzAppId].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAppId = Index("app_id", XyzMembers.XyzAppId.id, XyzMembers.XyzUpdatedAt.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `author` — index on the effective author and author timestamp, `_fn`, `_version`
         * (WHERE effective author IS NOT NULL). See [XyzMembers.XyzAuthor].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAuthor = Index("author", XyzMembers.XyzAuthor.id, XyzMembers.XyzAuthorTimestamp.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `tags` — inverted ([IndexType.TAG_LIST]) index over the `tags` member, supporting element
         * containment queries. See [XyzMembers.XyzTags].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzTags = Index("tags", XyzMembers.XyzTags.id)

        /**
         * `feature_type` — index on `ft`, `_fn`, `_version` (WHERE `ft IS NOT NULL`).
         * See [XyzMembers.XyzFeatureType].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzFeatureType = Index("feature_type", XyzMembers.XyzFeatureType.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `cv0` — index on custom numeric value 0, `_fn`, `_version` (WHERE `cv0 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue0 = Index("cv0", XyzMembers.XyzCustomValue0.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `cv1` — index on custom numeric value 1, `_fn`, `_version` (WHERE `cv1 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue1 = Index("cv1", XyzMembers.XyzCustomValue1.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `cv2` — index on custom numeric value 2, `_fn`, `_version` (WHERE `cv2 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue2 = Index("cv2", XyzMembers.XyzCustomValue2.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `cv3` — index on custom numeric value 3, `_fn`, `_version` (WHERE `cv3 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue3 = Index("cv3", XyzMembers.XyzCustomValue3.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `cs0` — index on custom string value 0, `_fn`, `_version` (WHERE `cs0 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString0 = Index("cs0", XyzMembers.XyzCustomString0.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `cs1` — index on custom string value 1, `_fn`, `_version` (WHERE `cs1 IS NOT NULL`).

         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString1 = Index("cs1", XyzMembers.XyzCustomString1.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `cs2` — index on custom string value 2, `_fn`, `_version` (WHERE `cs2 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString2 = Index("cs2", XyzMembers.XyzCustomString2.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `cs3` — index on custom string value 3, `_fn`, `_version` (WHERE `cs3 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString3 = Index("cs3", XyzMembers.XyzCustomString3.id, StandardMembers.FeatureNumberMember.id, StandardMembers.VersionMember.id)

        /**
         * `ref_point` — spatial index over the reference-point geometry member.
         * @since 3.0
         * @see [XyzMembers.XyzReferencePoint]
         */
        @JvmField @JsStatic
        val XyzReferencePoint = Index("ref_point", XyzMembers.XyzReferencePoint.id)

        /**
         * All indices for a default XYZ collection.
         * @since 3.0
         */
        @JvmField @JsStatic
        val ALL: List<Index> = listOf(
            XyzHereTile,
            XyzAppId,
            XyzAuthor,
            XyzTags,
            XyzFeatureType,
            XyzCustomValue0, XyzCustomValue1, XyzCustomValue2, XyzCustomValue3,
            XyzCustomString0, XyzCustomString1, XyzCustomString2, XyzCustomString3,
            XyzReferencePoint,
            StandardIndices.Geometry,
        )
    }
}
