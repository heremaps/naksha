@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL_NUMBER
import naksha.model.NakshaError
import naksha.model.NakshaException
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A map stores collections.
 */
@JsExport
open class PgMap internal constructor(
    /**
     * The reference to the storage.
     * @since 3.0.0
     */
    open val storage: PgStorage,

    /**
     * The HEAD state of the map.
     * @since 3.0.0
     */
    val nakshaMap: NakshaMap,

    /**
     * The OID of the collection-number sequence.
     * @since 3.0.0
     */
    colNumberSequenceOid: Int,

    /**
     * The map-id.
     */
    val id: String = nakshaMap.id,

    /**
     * The map-number.
     */
    val number: Int = nakshaMap.number ?: throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "map number missing"),
) {

    /**
     * The OID of the collection-number sequence.
     * @since 3.0.0
     */
    //var colNumberSequenceOid: Int = colNumberSequenceOid
    //    protected set

    private var _collections: PgCollection? = null

    /**
     * The collections' collection of the map.
     * @since 3.0.0
     */
    val collections: PgCollection
        get() {
            var c = _collections
            if (c == null) {
                c = PgCollection(this, NakshaCollection()
                    .withMapId(id)
                    .withId(COLLECTIONS_COL)
                    .withNumber(COLLECTIONS_COL_NUMBER))
                _collections = c
            }
            return c
        }

    /**
     * The map-identifier quoted optionally in double quotes.
     * @since 3.0.0
     */
    @JvmField
    val quotedId = quoteIdent(id)

    /**
     * Returns the current collection-number, so the last used one.
     * @param conn the connection to use to access the database.
     * @return the current _(last used)_ collection-number.
     * @since 3.0.0
     */
//    fun getCollectionNumber(conn: PgConnection): Int {
//        val QUERY = "SELECT currval($1) as colnum"
//        val cursor = conn.execute(QUERY, arrayOf(colNumberSequenceOid)).fetch()
//        cursor.use {
//            val number: Int = cursor["colnum"]
//            return number
//        }
//    }

    /**
     * Allocate a new collection-number.
     * @param conn the connection to use to access the database.
     * @return the allocated collection-number.
     * @since 3.0.0
     */
//    fun newCollectionNumber(conn: PgConnection): Int {
//        val QUERY = "SELECT nextval($1) as colnum"
//        val cursor = conn.execute(QUERY, arrayOf(colNumberSequenceOid)).fetch()
//        cursor.use {
//            val number: Int = cursor["colnum"]
//            return number
//        }
//    }

    // TODO: We should have alias methods to manage collections, that redirect to storage.adminMap.{name} !!!
}
