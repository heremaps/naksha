@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.AtomicMap
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.WeakRef
import naksha.base.fn.Fn0
import naksha.model.NakshaContext.NakshaContextCompanion.currentContext
import naksha.model.NakshaError.NakshaErrorCompanion.UNAUTHORIZED
import naksha.model.NakshaException
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport

/**
 * Information about the database and connection, that need only to be queried ones per session.
 * @property storage the reference to the storage to which the schema belongs.
 * @property name the name of the schema.
 */
@JsExport
abstract class PgSchema(val storage: PgStorage, val name: String) {
    /**
     * The lock internally used to synchronize access.
     */
    internal val lock = Platform.newLock()

    /**
     * The epoch time in milliseconds when the cache should be updated next, _null_ when no update where ever done.
     */
    private var _updateAt: Int64? = null

    /**
     * The schema
     */
    internal var _oid: Int? = null

    /**
     * The [OID](https://www.postgresql.org/docs/current/datatype-oid.html) (Object Identifier) of the schema.
     * @throws IllegalStateException if no such schema exists yet.
     */
    open val oid: Int
        get() {
            if (_updateAt == null) refresh()
            val _oid = this._oid
            check(_oid != null) { "The schema '$name' does not exist" }
            return _oid
        }

    /**
     * The quoted schema, for example `"foo"`, if no quoting is necessary, the string may be unquoted.
     */
    open val nameQuoted = PgUtil.quoteIdent(name)

    /**
     * A concurrent hash map with all managed collections of this schema.
     */
    internal val collections: AtomicMap<String, WeakRef<out PgCollection>> = Platform.newAtomicMap()

    /**
     * Returns the dictionaries' collection.
     * @return the dictionaries' collection.
     */
    open fun dictionaries(): PgNakshaDictionaries = getCollection(PgNakshaDictionaries.ID) { PgNakshaDictionaries(this) }

    /**
     * Returns the transactions' collection.
     * @return the transactions' collection.
     */
    open fun transactions(): PgNakshaTransactions = getCollection(PgNakshaTransactions.ID) { PgNakshaTransactions(this) }

    /**
     * Returns the collections' collection.
     * @return the collections' collection.
     */
    open fun collections(): PgNakshaCollections = getCollection(PgNakshaCollections.ID) { PgNakshaCollections(this) }

    /**
     * Returns a shared cached [PgCollection] wrapper. This method is internally called, when a storage or realm are initialized to create all internal collections.
     * @param id the collection identifier.
     * @return the shared and cached [PgCollection] wrapper.
     */
    open operator fun get(id: String): PgCollection = getCollection(id) {
        when(id) {
            PgNakshaDictionaries.ID -> PgNakshaDictionaries(this)
            PgNakshaCollections.ID -> PgNakshaCollections(this)
            PgNakshaTransactions.ID -> PgNakshaTransactions(this)
            else -> PgCollection(this, id)
        }
    }

    /**
     * Returns a shared cached [PgCollection] wrapper. This method is internally called, when a storage or realm are initialized to create all internal collections.
     * @param id the collection identifier.
     * @param constructor the constructor to the collection, if it does not exist already.
     * @return the shared and cached [PgCollection] wrapper.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T: PgCollection> getCollection(id: String, constructor: Fn0<T>): T {
        val collections = this.collections
        while (true) {
            var collection: PgCollection? = null
            val existingRef = collections[id]
            if (existingRef != null) collection = existingRef.deref()
            if (collection != null) return collection as T
            collection = constructor.call()
            val collectionRef = Platform.newWeakRef(collection)
            if (existingRef != null) {
                if (collections.replace(id, existingRef, collectionRef)) return collection
                // Conflict, another thread concurrently modified the cache.
            } else {
                collections.putIfAbsent(id, collectionRef) ?: return collection
                // Conflict, there is an existing reference, another thread concurrently access the cache.
            }
        }
    }

    private fun connOf(connection: PgConnection?): PgConnection {
        return connection ?: storage.newConnection(storage.defaultOptions.copy(schema = name)) { _, _ -> }
    }

    private fun closeOf(conn: PgConnection, connection: PgConnection?, commitBeforeClose: Boolean) {
        if (conn !== connection) {
            if (commitBeforeClose) conn.commit()
            conn.close()
        }
    }

    /**
     * Refresh the schema information cache. This is automatically called when any value is queries for the first time.
     * @param connection the connection to use to query information from the database; if _null_, a new connection is used temporary.
     * @return this.
     */
    open fun refresh(connection: PgConnection? = null): PgSchema {
        if (_updateAt == null || Platform.currentMillis() < _updateAt) {
            val conn = connOf(connection)
            try {
                val cursor = conn.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(name)).fetch()
                cursor.use {
                    _oid = cursor["oid"]
                }
            } finally {
                closeOf(conn, connection, false)
            }
            // We update every 15 seconds.
            _updateAt = Platform.currentMillis() + 15_000
        }
        return this
    }

    /**
     * Tests if the schema exists.
     * @return _true_ if the schema exists.
     */
    open fun exists(): Boolean {
        if (_updateAt == null) refresh()
        return _oid != null
    }

    /**
     * Initialize the schema, creating all necessary database tables, installing modules, ....
     *
     * The method does auto-commit, if no [connection] was given; otherwise committing must be done explicitly.
     * @param connection the connection to use to query information from the database; if _null_, a new connection is used temporary.
     */
    abstract fun init(connection: PgConnection? = null)

    /**
     * Drop the schema.
     *
     * The method does auto-commit, if no [connection] was given; otherwise committing must be done explicitly.
     * @param connection the connection to use to query information from the database; if _null_, a new connection is used temporary.
     */
    open fun drop(connection: PgConnection? = null) {
        check(currentContext().su) { throw NakshaException(UNAUTHORIZED, "Only superusers may drop schemata") }
        val conn = connOf(connection)
        try {
            conn.execute("DROP SCHEMA ${quoteIdent(name)}").close()
        } finally {
            closeOf(conn, connection, true)
        }
    }
}