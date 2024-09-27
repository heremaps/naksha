package naksha.psql.executors

import naksha.geo.SpGeometry
import naksha.model.Tuple
import naksha.model.objects.NakshaFeature
import naksha.model.request.Write
import naksha.psql.PgSession
import naksha.psql.PgUtil

/**
 * Data collection for all kind of row updates.
 */
internal data class RowForUpdate(
    /**
     * The write instructions as given by the client.
     */
    var write: Write,

    /**
     * The session.
     */
    var session: PgSession,
) {

    /**
     * The old row, if any.
     */
    var OLD: Tuple? = null

    /**
     * The cached old geometry, extracted from the row; if any.
     */
    var oldGeometry: SpGeometry? = null
        get() {
            var g = field
            if (g == null) {
                val old = OLD
                if (old != null) {
                    val old_g = old.geo
                    if (old_g != null) {
                        g = PgUtil.decodeGeometry(old_g, old.flags)
                        field = g
                    }
                }
            }
            return g
        }

    /**
     * The cached old feature, extracted from the row; if any.
     */
    var oldFeature: NakshaFeature? = null

    /**
     * The new row, if any.
     */
    var NEW: Tuple? = null

    /**
     * The cached new geometry.
     */
    var newGeometry: SpGeometry? = null

    /**
     * The cached new feature.
     */
    var newFeature: NakshaFeature? = null

}