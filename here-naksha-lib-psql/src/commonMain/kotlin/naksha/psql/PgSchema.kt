@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.CMap
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.WeakRef
import naksha.base.fn.Fn0
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
    open val schemaQuoted = PgUtil.quoteIdent(name)

    /**
     * A concurrent hash map with all managed collections of this schema.
     */
    internal val collections: CMap<String, WeakRef<out PgCollection>> = Platform.newCMap()

    /**
     * Returns the dictionaries' collection.
     * @return the dictionaries' collection.
     */
    open fun dictionaries(): NakshaDictionaries = getCollection(NakshaDictionaries.ID) { NakshaDictionaries(this) }

    /**
     * Returns the transactions' collection.
     * @return the transactions' collection.
     */
    open fun transactions(): NakshaTransactions = getCollection(NakshaTransactions.ID) { NakshaTransactions(this) }

    /**
     * Returns the collections' collection.
     * @return the collections' collection.
     */
    open fun collections(): NakshaCollections = getCollection(NakshaCollections.ID) { NakshaCollections(this) }

    /**
     * Returns a shared cached [PgCollection] wrapper. This method is internally called, when a storage or realm are initialized to create all internal collections.
     * @param id the collection identifier.
     * @return the shared and cached [PgCollection] wrapper.
     */
    open operator fun get(id: String): PgCollection = getCollection(id) {
        when(id) {
            NakshaDictionaries.ID -> NakshaDictionaries(this)
            NakshaCollections.ID -> NakshaCollections(this)
            NakshaTransactions.ID -> NakshaTransactions(this)
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

    /**
     * Refresh the schema information cache. This is automatically called when any value is queries for the first time.
     * @param conn the connection to use to query information from the database; if _null_, a new connection is used temporary.
     * @return this.
     */
    open fun refresh(conn: PgConnection? = null): PgSchema {
        if (_updateAt == null || Platform.currentMillis() < _updateAt) {
            val sql = "SELECT oid FROM pg_namespace WHERE nspname = $1"
            val c = conn ?: storage.newConnection(storage.defaultOptions.copy(schema = name)) { _, _ -> }
            try {
                val cursor = c.execute(sql, arrayOf(name)).fetch()
                cursor.use {
                    _oid = cursor["schema_oid"]
                }
            } finally {
                if (c !== conn) c.close()
            }
            // We update every 15 seconds.
            _updateAt = Platform.currentMillis() + 15_000
        }
        return this
    }

    open fun exists(): Boolean {
        if (_updateAt == null) refresh()
        return _oid != null
    }

    abstract fun init()

    open fun drop() {
        TODO("Not implemented yet")
    }
}