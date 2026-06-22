@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The default indices created for a Data-Hub (XYZ) compatible collection.
 *
 * This is the index counterpart of [XyzMembers]: it indexes the members declared there (e.g.
 * [XyzMembers.XyzTags], [XyzMembers.XyzAppId], [XyzMembers.XyzHereTile]) and is applied via
 * [NakshaCollection.withXyzIndices].
 *
 * An index refers to a member by its identity (name + type), not by JSON path, so indices that
 * target a member which is also standard (e.g. the geometry member, see [StandardIndices.Geometry])
 * are **referenced** from [StandardIndices] rather than redeclared here. The storage-managed indices
 * that every collection always has live in [StandardIndices.MANDATORY].
 * @since 3.0
 */
@JsExport
class XyzIndices private constructor() {

    companion object XyzIndices_C {

        /**
         * `here_tile` — index on `here_tile`, `fn`, `version` (WHERE `here_tile IS NOT NULL`).
         * See [XyzMembers.XyzHereTile].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzHereTile = Index("here_tile", IndexType.BTREE, "here_tile", "fn", "version")

        /**
         * `app_id` — index on `app_id`, `updated_at`, `fn`, `version` (WHERE `app_id IS NOT NULL`).
         * See [XyzMembers.XyzAppId].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAppId = Index("app_id", IndexType.BTREE, "app_id", "updated_at", "fn", "version")

        /**
         * `author` — index on the effective author and author timestamp, `fn`, `version`
         * (WHERE effective author IS NOT NULL). See [XyzMembers.XyzAuthor].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzAuthor = Index("author", IndexType.BTREE, "author", "author_ts", "fn", "version")

        /**
         * `tags` — inverted ([IndexType.TAG_LIST]) index over the `tags` member, supporting element
         * containment queries. See [XyzMembers.XyzTags].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzTags = Index("tags", IndexType.TAG_LIST, "tags")

        /**
         * `feature_type` — index on `ft`, `fn`, `version` (WHERE `ft IS NOT NULL`).
         * See [XyzMembers.XyzFeatureType].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzFeatureType = Index("feature_type", IndexType.BTREE, "ft", "fn", "version")

        /**
         * `cv0` — index on custom numeric value 0, `fn`, `version` (WHERE `cv0 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue0 = Index("cv0", IndexType.BTREE, "cv0", "fn", "version")

        /**
         * `cv1` — index on custom numeric value 1, `fn`, `version` (WHERE `cv1 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue1 = Index("cv1", IndexType.BTREE, "cv1", "fn", "version")

        /**
         * `cv2` — index on custom numeric value 2, `fn`, `version` (WHERE `cv2 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue2 = Index("cv2", IndexType.BTREE, "cv2", "fn", "version")

        /**
         * `cv3` — index on custom numeric value 3, `fn`, `version` (WHERE `cv3 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomValue3 = Index("cv3", IndexType.BTREE, "cv3", "fn", "version")

        /**
         * `cs0` — index on custom string value 0, `fn`, `version` (WHERE `cs0 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString0 = Index("cs0", IndexType.BTREE, "cs0", "fn", "version")

        /**
         * `cs1` — index on custom string value 1, `fn`, `version` (WHERE `cs1 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString1 = Index("cs1", IndexType.BTREE, "cs1", "fn", "version")

        /**
         * `cs2` — index on custom string value 2, `fn`, `version` (WHERE `cs2 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString2 = Index("cs2", IndexType.BTREE, "cs2", "fn", "version")

        /**
         * `cs3` — index on custom string value 3, `fn`, `version` (WHERE `cs3 IS NOT NULL`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzCustomString3 = Index("cs3", IndexType.BTREE, "cs3", "fn", "version")

        /**
         * `ref_point` — spatial ([IndexType.SPATIAL]) index over the reference-point geometry member
         * (WHERE `ref_point IS NOT NULL`). See [XyzMembers.XyzReferencePoint].
         * @since 3.0
         */
        @JvmField @JsStatic
        val XyzReferencePoint = Index("ref_point", IndexType.SPATIAL, "ref_point")

        /**
         * All indices for a default XYZ collection, in declaration order: the [StandardIndices.MANDATORY]
         * indices (always present), followed by the XYZ default indices, followed by the geometry index
         * (referenced from [StandardIndices.Geometry], since geometry is a standard member).
         *
         * Does **not** include the [StandardIndices.SPECIAL] indices (`pn`/`pt`/`gv`), which are declared
         * explicitly only where needed (e.g. `naksha~transactions`).
         * @since 3.0
         */
        @JvmField @JsStatic
        val ALL: List<Index> = StandardIndices.MANDATORY + listOf(
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
