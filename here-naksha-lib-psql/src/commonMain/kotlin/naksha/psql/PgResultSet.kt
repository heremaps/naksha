@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Guid
import naksha.model.objects.NakshaFeature
import naksha.model.request.ResultRow
import naksha.model.request.ResultSet
import naksha.model.request.RowOptions
import kotlin.js.JsExport

@JsExport
class PgResultSet(
    storage: PgStorage,
    rowOptions: RowOptions,
    private var _rows: MutableList<ResultRow>,
    private var _handle: String? = null,
) : ResultSet {
    // TODO: Improve with implementation!

    override fun isFetched(): Boolean = true

    override fun handle(): String? = _handle

    override fun size(): Int = _rows?.size ?: 0

    private var _guids: List<Guid>? = null

    override fun guids(): List<Guid> {
        var list = _guids
        if (list == null) {
            val l = mutableListOf<Guid>()
            for (row in rows()) {
                val guid = row.row?.guid
                if (guid != null) l.add(guid)
            }
            list = l.toList()
            _guids = list
        }
        return list
    }

    override fun rows(): MutableList<ResultRow> = _rows

    private var _features: MutableList<NakshaFeature>? = null

    override fun features(): MutableList<NakshaFeature> {
        var list = _features
        if (list == null) {
            list = mutableListOf()
            for (row in rows()) {
                val feature = row.getFeature()
                if (feature != null)  list.add(feature)
            }
            this._features = list
        }
        return list
    }
}