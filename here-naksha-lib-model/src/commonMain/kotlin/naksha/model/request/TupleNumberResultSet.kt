@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.PAnyMap
import naksha.base.AnyObjectList
import naksha.base.TupleNumber
import naksha.base.illegalArg
import naksha.base.unsupportedOp
import naksha.model.ISession
import naksha.model.IStorage
import naksha.model.TupleNumberList
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.math.max
import kotlin.math.min

/**
 * A very simple basic implementation of a result-set for storages that have tuple and support [loadTuples][ISession.loadTuples] calls.
 * @since 3.0
 */
@JsExport
class TupleNumberResultSet(
    /** The request for which the result-set is. */
    request: Request,
    /** The storage for which the result-set is _(can be used to query for the database, catalog or collection of tuples)_. */
    storage: IStorage,
    /** The session for which the result-set is. */
    session: ISession,
    /** The tuple-numbers that are the result-set. */
    private val tupleNumbers: TupleNumberList,
) : ResultSet(request, storage, session, tupleNumbers.size) {
    override val offset: Int? = null
    override val totalSize: Long? = null
    override val handle: ByteArray? = null
    override val supportTuple: Boolean = true

    override fun getFeatureNumber(i: Int): Long = tupleNumbers.getFeatureNumber(i)
    override fun getVersion(i: Int): Long = tupleNumbers.getVersion(i)
    override fun getTupleNumber(i: Int): TupleNumber = tupleNumbers[i] ?: throw illegalArg("No tuple-number at index $i")
    override fun getTupleNumbers(from: Int, to: Int): ITupleNumberArray {
        val tupleNumbers = this.tupleNumbers
        val _from = max(0, min(from, to))
        val _to = min(tupleNumbers.size, max(from, to))
        val _newSize = _to - _from
        if (_from == 0 && _to == tupleNumbers.size) return tupleNumbers
        val copy = TupleNumberList()
        copy.setCapacity(_newSize)
        for (i in _from.._to) {
            copy.add(tupleNumbers[i])
        }
        return copy
    }

    override fun getBytes(from: Int, to: Int): ByteArray {
        throw unsupportedOp("Serialization of ResultSet not support by storage")
    }

    override fun getObjects(
        from: Int,
        to: Int,
        limit: Int,
        tupleFilter: ITupleFilter?,
        objectFilter: IObjectFilter?
    ): AnyObjectList {
        val tupleNumbers = getTupleNumbers(from, to)
        val tuples = session.loadTuples(tupleNumbers, false)
        val objects = AnyObjectList()
        objects.setCapacity(tuples.size)
        for (tuple in tuples) {
            if (tuple == null) continue
            var obj: PAnyMap? = null
            if (tupleFilter != null || objectFilter != null) {
                val tn = tuple.tupleNumber
                val catalog = session.getCatalogByNumber(tn.catalogNumber)
                if (catalog != null) {
                    val collection = session.getCollectionByNumber(catalog, tn.collectionNumber)
                    if (collection != null) {
                        if (tupleFilter != null && !tupleFilter.keepTuple(collection, tuple)) continue
                        if (objectFilter != null) {
                            obj = objectFilter.filter(collection,  tuple.decodeObject(null))
                            if (obj == null) continue
                        }
                    }
                }
            }
            if (obj == null) obj = tuple.decodeObject(null)
            objects.add(obj)
        }
        return objects
    }
}