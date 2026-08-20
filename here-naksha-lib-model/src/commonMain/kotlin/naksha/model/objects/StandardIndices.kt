@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The canonical, storage-independent indices that every Naksha storage understands.
 *
 * **Mandatory members are indexed implicitly.** Every storage must, on its own, index the mandatory
 * members — [feature-number][StandardMembers.FeatureNumber], [version][StandardMembers.FeatureVersion],
 * [next-version][StandardMembers.NextVersion] and [id][StandardMembers.Id] — so that lookups and range
 * queries on them are fast. Consumers may rely on this without declaring any index; there is therefore
 * nothing to declare here and [MANDATORY] is intentionally empty. (The
 * [global-book-number][StandardMembers.GlobalBookFeatureNumber] member is stored but deliberately **not**
 * indexed — it is only read by background maintenance, which tolerates a full scan.)
 *
 * What remains are **offers**, never mandatory: the standard optional [Geometry] index over the standard
 * [StandardMembers.Geometry] member (recommended for GeoJSON data, and freely editable), and the [SPECIAL]
 * indices that a collection declares explicitly when it needs them (e.g. `naksha~transactions`). The
 * default index set for a Data-Hub (XYZ) compatible collection lives in [XyzIndices], the index
 * counterpart of [XyzMembers].
 * @since 3.0
 */
@JsExport
class StandardIndices private constructor() {

    companion object StandardIndices_C {

        /**
         * Mandatory indices to inject into every collection — intentionally empty: the mandatory
         * members are indexed implicitly by the storage itself (see the class documentation), so there
         * is nothing to add on top. Kept as an (empty) list so index-composition code stays generic.
         *
         * Custom indices cannot collide with the storage-internal ones: internal index and member
         * names are reserved by a leading underscore, and client-supplied names are validated against
         * [naksha.model.NakshaIdType.INDEX] / [naksha.model.NakshaIdType.MEMBER] (which forbid a leading
         * underscore).
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
         * `pn` — index over the publish-number member, for efficient ordering and range queries by
         * publish-number. Used in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val PublishNumber = Index("pn", StandardMembers.PublishNumber.name)

        /**
         * `pt` — index over the publish-time member, for efficient range queries by publish-time.
         * Used in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val PublishTime = Index("pt", StandardMembers.PublishTime.name)

        /**
         * `gv` — index over the HERE global-version member, for efficient range queries by global
         * version. Used in `naksha~transactions`.
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
