@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * Abstract interface to a collection administrative object.
 */
@JsExport
interface ICollection {
    /**
     * The map in which the collection is located.
     */
    val map: IMap

    /**
     * The identifier of the collection.
     */
    val id: String

    /**
     * The collection-number, a value between 0 and 1,099,511,627,775 (_2^40-1_).
     */
    val number: Int64

    /**
     * The latest known state (HEAD state).
     */
    val nakshaCollection: NakshaCollection

    /**
     * Tests if this collection exists.
     * @return _true_ if the collection exists.
     */
    fun exists(): Boolean
}