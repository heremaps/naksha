@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The canonical set of standard indices that every Naksha storage understands.
 *
 * Indices are divided into two groups:
 *
 * ### Mandatory indices
 * These are always created by the storage regardless of the [NakshaCollection.indices] list.
 * Clients must not declare them manually. They are marked `internal = true` in the storage layer.
 *
 * - [StandardIndices_C.FeatureNumberUnique] — PRIMARY KEY on `fn` (all tables)
 * - [StandardIndices_C.IdUnique] — UNIQUE on `id` WHERE `id IS NOT NULL` (HEAD / DELETED / META tables)
 * - [StandardIndices_C.Id] — non-unique index on `id`, `fn`, `version` WHERE `id IS NOT NULL` (HISTORY tables)
 * - [StandardIndices_C.Version] — non-unique index on `version` (all tables)
 * - [StandardIndices_C.GlobalBookNumber] — conditional non-unique index on `gbn` WHERE `gbn IS NOT NULL` (all tables)
 *
 * ### Special indices
 * These are **not** created by default. They must be explicitly declared in
 * [NakshaCollection.indices] and are defined here so that all storage implementations share
 * a consistent name and type contract.
 *
 * - [StandardIndices_C.PublishNumber] — BTREE on `pn` (WHERE `pn IS NOT NULL`)
 * - [StandardIndices_C.PublishTime]   — BTREE on `pt` (WHERE `pt IS NOT NULL`)
 * - [StandardIndices_C.GlobalVersion] — BTREE on `gv` (WHERE `gv IS NOT NULL`)
 *
 * ### Default indices
 * These are created automatically when [NakshaCollection.indices] is `null` (backward-compatible
 * full schema). When [NakshaCollection.indices] is explicitly set (even to an empty list), only
 * the mandatory indices plus the explicitly declared indices are created.
 *
 * - [StandardIndices_C.HereTile] — `here_tile`, `fn`, `version`
 * - [StandardIndices_C.AppId] — `app_id`, `updated_at`, `fn`, `version`
 * - [StandardIndices_C.Author] — `author`, `author_ts`, `fn`, `version`
 * - [StandardIndices_C.Tags] — GIN tags index
 * - [StandardIndices_C.FeatureType] — `ft`, `fn`, `version`
 * - [StandardIndices_C.CustomValue0] .. [StandardIndices_C.CustomValue3] — custom numeric values
 * - [StandardIndices_C.CustomString0] .. [StandardIndices_C.CustomString3] — custom string values
 * - [StandardIndices_C.ReferencePoint] — SP-GIST reference-point geometry
 * - [StandardIndices_C.GistGeometry] — GIST geometry
 *
 * @since 3.0
 */
@JsExport
class StandardIndices private constructor() {

    companion object StandardIndices_C {

        // -------------------------------------------------------------------------
        // Mandatory indices — storage-managed, always present, internal = true
        // -------------------------------------------------------------------------

        /**
         * `fn_unique` — PRIMARY KEY on the feature-number. Present in all tables. Mandatory.
         * @since 3.0
         */
        @JvmField @JsStatic
        val FeatureNumberUnique = Index("fn_unique", IndexType.BTREE, "fn").withInternal(true)

        /**
         * `id_unique` — UNIQUE index on `id` (WHERE `id IS NOT NULL`). Present in HEAD, DELETED, and
         * META tables. Mandatory.
         * @since 3.0
         */
        @JvmField @JsStatic
        val IdUnique = Index("id_unique", IndexType.BTREE, "id").withInternal(true).withUnique(true)

        /**
         * `id` — non-unique index on `id`, `fn`, `version` (WHERE `id IS NOT NULL`). Present in
         * HISTORY tables. Mandatory.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Id = Index("id", IndexType.BTREE, "id", "fn", "version").withInternal(true)

        /**
         * `version` — non-unique index on `version`. Present in all tables. Mandatory.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Version = Index("version", IndexType.BTREE, "version").withInternal(true)

        /**
         * `gbn` — conditional non-unique index on `gbn` WHERE `gbn IS NOT NULL`. Used by the sequencer
         * to efficiently locate all tuples that reference a particular global-book. Present in all
         * tables. Mandatory.
         * @since 3.0
         */
        @JvmField @JsStatic
        val GlobalBookNumber = Index("gbn", IndexType.BTREE, "gbn").withInternal(true)

        /**
         * All mandatory indices, in declaration order. These are always created by the storage and must
         * not appear in [NakshaCollection.indices].
         * @since 3.0
         */
        @JvmField @JsStatic
        val MANDATORY: List<Index> = listOf(FeatureNumberUnique, IdUnique, Id, Version, GlobalBookNumber)

        /**
         * The names of all [MANDATORY] indices, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val MANDATORY_NAMES: Set<String> = MANDATORY.map { it.name }.toHashSet()

        // -------------------------------------------------------------------------
        // Special indices — not in MANDATORY or DEFAULT; declared explicitly per collection
        // -------------------------------------------------------------------------

        /**
         * `pn` — BTREE index on `pn` (WHERE `pn IS NOT NULL`). Enables efficient ordering and
         * range scans by publish-number. Used in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val PublishNumber = Index("pn", IndexType.BTREE, "pn")

        /**
         * `pt` — BTREE index on `pt` (WHERE `pt IS NOT NULL`). Enables efficient range scans
         * by publish-time. Used in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val PublishTime = Index("pt", IndexType.BTREE, "pt")

        /**
         * `gv` — BTREE index on `gv` (WHERE `gv IS NOT NULL`). Enables efficient range scans
         * by HERE global version. Used in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val GlobalVersion = Index("gv", IndexType.BTREE, "gv")

        // -------------------------------------------------------------------------
        // Default indices — created when NakshaCollection.indices is null
        // -------------------------------------------------------------------------

        /**
         * `here_tile` — index on `here_tile`, `fn`, `version` (WHERE `here_tile IS NOT NULL`).
         * Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val HereTile = Index("here_tile", IndexType.BTREE, "here_tile", "fn", "version")

        /**
         * `app_id` — index on `app_id`, `updated_at`, `fn`, `version` (WHERE `app_id IS NOT NULL`).
         * Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val AppId = Index("app_id", IndexType.BTREE, "app_id", "updated_at", "fn", "version")

        /**
         * `author` — index on the effective author and author timestamp, `fn`, `version`
         * (WHERE effective author IS NOT NULL). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Author = Index("author", IndexType.BTREE, "author", "author_ts", "fn", "version")

        /**
         * `tags` — inverted ([IndexType.SET]) index over the `tags` member (a `jsonb` array
         * of unique strings). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Tags = Index("tags", IndexType.SET, "tags")

        /**
         * `feature_type` — index on `ft`, `fn`, `version` (WHERE `ft IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val FeatureType = Index("feature_type", IndexType.BTREE, "ft", "fn", "version")

        /**
         * `cv0` — index on custom numeric value 0, `fn`, `version` (WHERE `cv0 IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomValue0 = Index("cv0", IndexType.BTREE, "cv0", "fn", "version")

        /**
         * `cv1` — index on custom numeric value 1, `fn`, `version` (WHERE `cv1 IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomValue1 = Index("cv1", IndexType.BTREE, "cv1", "fn", "version")

        /**
         * `cv2` — index on custom numeric value 2, `fn`, `version` (WHERE `cv2 IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomValue2 = Index("cv2", IndexType.BTREE, "cv2", "fn", "version")

        /**
         * `cv3` — index on custom numeric value 3, `fn`, `version` (WHERE `cv3 IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomValue3 = Index("cv3", IndexType.BTREE, "cv3", "fn", "version")

        /**
         * `cs0` — index on custom string value 0, `fn`, `version` (WHERE `cs0 IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomString0 = Index("cs0", IndexType.BTREE, "cs0", "fn", "version")

        /**
         * `cs1` — index on custom string value 1, `fn`, `version` (WHERE `cs1 IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomString1 = Index("cs1", IndexType.BTREE, "cs1", "fn", "version")

        /**
         * `cs2` — index on custom string value 2, `fn`, `version` (WHERE `cs2 IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomString2 = Index("cs2", IndexType.BTREE, "cs2", "fn", "version")

        /**
         * `cs3` — index on custom string value 3, `fn`, `version` (WHERE `cs3 IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomString3 = Index("cs3", IndexType.BTREE, "cs3", "fn", "version")

        /**
         * `ref_point` — spatial ([IndexType.SPATIAL]) index over the reference-point geometry member
         * (WHERE `ref_point IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val ReferencePoint = Index("ref_point", IndexType.SPATIAL, "ref_point")

        /**
         * `gist_geo` — spatial ([IndexType.SPATIAL]) GIST index over the geometry member
         * (WHERE `geo IS NOT NULL`). Default index.
         * @since 3.0
         */
        @JvmField @JsStatic
        val GistGeometry = Index("gist_geo", IndexType.SPATIAL, "geo")

        /**
         * All default indices (created when [NakshaCollection.indices] is `null`), in declaration order.
         *
         * Does **not** include the [MANDATORY] indices — those are always present regardless.
         * @since 3.0
         */
        @JvmField @JsStatic
        val DEFAULT: List<Index> = listOf(
            HereTile,
            AppId,
            Author,
            Tags,
            FeatureType,
            CustomValue0, CustomValue1, CustomValue2, CustomValue3,
            CustomString0, CustomString1, CustomString2, CustomString3,
            ReferencePoint,
            GistGeometry,
        )

        /**
         * The names of all [DEFAULT] indices, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val DEFAULT_NAMES: Set<String> = DEFAULT.map { it.name }.toHashSet()

        /**
         * All special indices — not added automatically but recognised by all storage implementations.
         * @since 3.0
         */
        @JvmField @JsStatic
        val SPECIAL: List<Index> = listOf(PublishNumber, PublishTime, GlobalVersion)

        /**
         * The names of all [SPECIAL] indices, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val SPECIAL_NAMES: Set<String> = SPECIAL.map { it.name }.toHashSet()

        /**
         * All standard indices: [MANDATORY] followed by [DEFAULT] followed by [SPECIAL].
         * @since 3.0
         */
        @JvmField @JsStatic
        val ALL: List<Index> = MANDATORY + DEFAULT + SPECIAL

        /**
         * The names of all standard indices, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val ALL_NAMES: Set<String> = ALL.map { it.name }.toHashSet()
    }
}
