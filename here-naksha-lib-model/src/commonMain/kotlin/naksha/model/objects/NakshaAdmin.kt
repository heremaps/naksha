package naksha.model.objects

import naksha.base.Id
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_ID
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_ID
import naksha.base.TupleNumber
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The representation of the virtual `naksha~admin` catalog within a [database][NakshaDatabase]. It always contains the following virtual collection:
 * - [collections] — The virtual collection that contains the collections of the catalog `naksha~admin`; this is immutable and limited to what is exposed here.
 * - [catalogs] — The collection that stores the metadata about all [catalogs][NakshaCatalog] being part of the database.
 * - [transactions] — The collection that stores the [transactions][NakshaTx] that have been executed in the storage.
 * - [books] — The collection that stores the global books used to encode/decode objects stored in the database.
 * @since 3.0
 * @see NakshaAdminCatalogs
 * @see NakshaAdminCollections
 * @see NakshaAdminTransactions
 * @see NakshaAdminBooks
 */
@JsExport
class NakshaAdmin @Deprecated("This constructor is for reflection only") constructor(): NakshaCatalog() {
    /**
     * Creates the representation of the virtual `naksha~admin` catalog.
     * @param databaseId the [Id] of the database for which to create the virtual administrative entities.
     * @since 3.0
     */
    @JsName("newNakshaAdmin")
    constructor(databaseId: Id): this() {
        this._dbId = databaseId
        this.databaseId = dbId.text
        this.id = ADMIN_CATALOG_ID.text
        this.tupleNumber = TupleNumber(dbId, ADMIN_CATALOG_ID, COLLECTIONS_COL_ID, ADMIN_CATALOG_ID, 1L)
    }

    private var _dbId: Id? = null
    private val dbId: Id
        get() {
            var dbId = _dbId
            if (dbId == null) { // Was restored from serialized JSON
                dbId = Id(this.id)
                _dbId = dbId
            }
            return dbId
        }

    private var _collections: NakshaAdminCollections? = null
    /**
     * The `admin~collections` collection that stores all collections of the admin catalog (`naksha~admin`).
     *
     * This is a virtual immutable collection that will only have four entries, being the ones as well referred here.
     * @ince 3.0
     */
    val collections: NakshaAdminCollections
        get() {
            var col = _collections
            if (col == null) {
                col = NakshaAdminCollections(dbId)
                _collections = col
            }
            return col
        }

    private var _catalogs: NakshaAdminCatalogs? = null
    /**
     * The `admin~catalogs` collection that stores all catalogs of the storage.
     *
     * Can be used by clients to manage collections.
     * @ince 3.0
     */
    val catalogs: NakshaAdminCatalogs
        get() {
            var col = _catalogs
            if (col == null) {
                col = NakshaAdminCatalogs(dbId)
                _catalogs = col
            }
            return col
        }

    private var _transactions: NakshaAdminTransactions? = null
    /**
     * The `admin~transactions` collection that stores all [transactions][NakshaTx] that happened in the storage.
     *
     * Can be used by clients to manager transactions.
     * @ince 3.0
     */
    val transactions: NakshaAdminTransactions
        get() {
            var col = _transactions
            if (col == null) {
                col = NakshaAdminTransactions(dbId)
                _transactions = col
            }
            return col
        }

    private var _books: NakshaAdminBooks? = null
    /**
     * The `admin~books` collection that stores all global books that are in the storage.
     *
     * Can be used by clients to manager encodeing/decoding dictionaries, aka books.
     * @ince 3.0
     */
    val books: NakshaAdminBooks
        get() {
            var col = _books
            if (col == null) {
                col = NakshaAdminBooks(dbId)
                _books = col
            }
            return col
        }
}