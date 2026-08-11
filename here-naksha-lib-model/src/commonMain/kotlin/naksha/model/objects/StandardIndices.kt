@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The canonical, storage-managed indices that every Naksha storage understands.
 *
 * These are flavour-independent: the [MANDATORY] indices are always present, the standard optional
 * [Geometry] indexes the standard [StandardMembers.Geometry] member, and the [SPECIAL] indices
 * are declared explicitly per collection (e.g. `naksha~transactions`). The default index set for a
 * Data-Hub (XYZ) compatible collection lives in [XyzIndices], the index counterpart of [XyzMembers].
 * @since 3.0
 */
@JsExport
class StandardIndices private constructor() {

    companion object StandardIndices_C {

        // Reserved names only — the table DDL already provides these ($c_pkey covers fn_unique;
        // $c_id covers id_unique and id; $i_version covers version). Kept so custom indices can't
        // collide with them; NOT part of [MANDATORY].

        /** `fn_unique` — reserved name; the `$c_pkey` PRIMARY KEY covers it. @since 3.0 */
        @JvmField @JsStatic
        val FeatureNumberUnique = Index("fn_unique", StandardMembers.FeatureNumber.name).withInternal(true).withUnique(true)

        /** `id_unique` — reserved name; the `$c_id` UNIQUE constraint covers it. @since 3.0 */
        @JvmField @JsStatic
        val IdUnique = Index("id_unique", StandardMembers.Id.name).withInternal(true).withUnique(true)

        /** `id` — reserved name; the `$c_id` constraint covers it (id key + INCLUDE version, fn). @since 3.0 */
        @JvmField @JsStatic
        val Id = Index("id", StandardMembers.Id.name, StandardMembers.FeatureNumber.name, StandardMembers.FeatureVersion.name).withInternal(true)

        /** `version` — reserved name; the `$i_version` btree index covers it. @since 3.0 */
        @JvmField @JsStatic
        val Version = Index("version", StandardMembers.FeatureVersion.name).withInternal(true)

        /**
         * `gbn` — partial index (`WHERE gbn IS NOT NULL`) used by the sequencer. Created by the
         * table DDL as `$i_gbn`; reserved name, not created via [MANDATORY].
         * @since 3.0
         */
        @JvmField @JsStatic
        val GlobalBookNumber = Index("gbn", StandardMembers.GlobalBookFeatureNumber.name).withInternal(true).withPartial(true)

        /**
         * Mandatory indices injected into every collection — now empty: all intrinsic indexes are
         * provided by the table DDL (constraints + `$i_gbn`). Kept so `indicesFor()` stays generic.
         * @since 3.0
         */
        @JvmField @JsStatic
        val MANDATORY: List<Index> = emptyList()

        /**
         * The names of all [MANDATORY] indices, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val MANDATORY_NAMES: Set<String> = MANDATORY.map { it.name }.toHashSet()

        /**
         * Intrinsic index names a custom index must not reuse.
         * @since 3.0
         */
        @JvmField @JsStatic
        val RESERVED_NAMES: Set<String> =
            listOf(FeatureNumberUnique.name, IdUnique.name, Id.name, Version.name, GlobalBookNumber.name).toHashSet()

        // -------------------------------------------------------------------------
        // Standard optional indices — index a standard optional member (see StandardMembers).
        // -------------------------------------------------------------------------

        /**
         * `geo` — spatial index over the geometry member.
         *
         * Geometry is a **standard** member (part of the GeoJSON standard, see [StandardMembers.Geometry]).
         * @since 3.0
         */
        @JvmField @JsStatic
        val Geometry = Index("geo", StandardMembers.Geometry.name)

        // -------------------------------------------------------------------------
        // Special indices — not added automatically; declared explicitly per collection
        // (e.g. naksha~transactions). The default XYZ index set lives in XyzIndices.
        // -------------------------------------------------------------------------

        /**
         * `pn` — BTREE index on `pn` (WHERE `pn IS NOT NULL`). Enables efficient ordering and
         * range scans by publish-number. Used in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val PublishNumber = Index("pn", StandardMembers.PublishNumber.name)

        /**
         * `pt` — BTREE index on `pt` (WHERE `pt IS NOT NULL`). Enables efficient range scans
         * by publish-time. Used in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val PublishTime = Index("pt", StandardMembers.PublishTime.name)

        /**
         * `gv` — BTREE index on `gv` (WHERE `gv IS NOT NULL`). Enables efficient range scans
         * by HERE global version. Used in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val GlobalVersion = Index("gv", StandardMembers.GlobalVersion.name)

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
    }
}
