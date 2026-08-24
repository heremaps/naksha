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
 * queries on them are fast. Consumers may rely on this without declaring any index. (The
 * [global-book-number][StandardMembers.GlobalBookFeatureNumber] member is stored but deliberately **not**
 * indexed — it is only read by background maintenance, which tolerates a full scan.)
 *
 * Everything declared here is therefore an **offer**, never mandatory: [Geometry] is the recommended
 * index for GeoJSON data, while [PublishNumber], [PublishTime] and [GlobalVersion] are the standard
 * indices a collection declares explicitly when it needs them (e.g. `naksha~transactions`). The default
 * index set for a Data-Hub (XYZ) compatible collection lives in [XyzIndices], the index counterpart of
 * [XyzMembers].
 * @since 3.0
 */
@JsExport
class StandardIndices private constructor() {

    companion object StandardIndices_C {

        /**
         * `geo` — spatial index over the geometry member.
         *
         * Geometry is a **standard** member (part of the GeoJSON standard, see [StandardMembers.Geometry]).
         * @since 3.0
         */
        @JvmField @JsStatic
        val Geometry = Index("geo", StandardMembers.Geometry.name)

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
    }
}
