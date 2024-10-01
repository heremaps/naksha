@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fn0
import naksha.jbon.IDictManager
import naksha.jbon.JbDictionary
import naksha.model.IMap
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS_NUMBER
import naksha.model.Naksha.NakshaCompanion.VIRT_DICTIONARIES
import naksha.model.Naksha.NakshaCompanion.VIRT_DICTIONARIES_NUMBER
import naksha.model.Naksha.NakshaCompanion.VIRT_TRANSACTIONS
import naksha.model.Naksha.NakshaCompanion.VIRT_TRANSACTIONS_NUMBER
import naksha.model.NakshaContext.NakshaContextCompanion.currentContext
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.NOT_IMPLEMENTED
import naksha.model.NakshaError.NakshaErrorCompanion.UNAUTHORIZED
import naksha.model.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.model.NakshaException
import naksha.model.NakshaVersion
import naksha.model.SessionOptions
import naksha.model.objects.NakshaFeature
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Information about the database and connection, that need only to be queried ones per session.
 */
@JsExport
open class PgMap(
    /**
     * The reference to the storage to which the schema belongs.
     */
    override val storage: PgStorage,

    /**
     * The map-id.
     */
    override val id: String,

    /**
     * The name of the schema.
     */
    val schemaName: String
) : IMap {
    /**
     * Admin options for this schema aka map.
     */
    fun adminOptions(): SessionOptions = storage.adminOptions.copy(mapId = id)

    internal var _number: Int? = null

    /**
     * The map-number of this map.
     */
    override val number: Int
        get() {
            if (!exists()) throw NakshaException(MAP_NOT_FOUND, "The map '$id' does not exist")
            return _number ?: throw NakshaException(MAP_NOT_FOUND, "The map '$id' does not exist")
        }

    /**
     * The lock internally used to synchronize access.
     */
    internal val lock = Platform.newLock()

    /**
     * The epoch time in milliseconds when the cache should be updated next, _null_ when no update where ever done.
     */
    private var _updateAt: Int64? = null

    internal var _oid: Int? = null

    /**
     * The [OID](https://www.postgresql.org/docs/current/datatype-oid.html) (Object Identifier) of the schema.
     * @throws IllegalStateException if no such schema exists yet.
     */
    open val oid: Int
        get() {
            if (!exists()) throw NakshaException(MAP_NOT_FOUND, "The map '$id' does not exist")
            return _oid ?: throw NakshaException(MAP_NOT_FOUND, "The map '$id' does not exist")
        }

    private var _collectionNumberSeqOid: Int? = null

    /**
     * The OID of the collection-number sequence.
     */
    open val collectionNumberSeqOid: Int
        get() {
            if (!exists()) throw NakshaException(MAP_NOT_FOUND, "The map '$id' does not exist")
            return _collectionNumberSeqOid ?: throw NakshaException(MAP_NOT_FOUND, "The map '$id' does not exist")
        }

    /**
     * Uses the given connection to acquire a new collection-number.
     * @param conn the connection to use to acquire the number.
     * @return the new collection number.
     */
    fun newCollectionNumber(conn: PgConnection): Int64 {
        val cursor = conn.execute("SELECT nextval($1) as col_number", arrayOf(collectionNumberSeqOid)).fetch()
        return cursor["col_number"]
    }

    /**
     * Test if this is the default schema.
     */
    fun isDefault(): Boolean = schemaName == storage.defaultSchemaName

    /**
     * The quoted schema, for example `"foo"`, if no quoting is necessary, the string may be unquoted.
     */
    open val nameQuoted = quoteIdent(schemaName)

    /**
     * A concurrent hash map with all cached collections by their `id`.
     */
    internal val collections: AtomicMap<String, WeakRef<PgCollection>> = Platform.newAtomicMap()

    /**
     * A concurrent hash map with all cached collection-identifiers by their `number`.
     */
    internal val collectionIdByNumber: AtomicMap<Int64, String> = Platform.newAtomicMap()

    /**
     * Returns the dictionaries' collection.
     * @return the dictionaries' collection.
     */
    open fun dictionaries(): PgNakshaDictionaries = getCollection(VIRT_DICTIONARIES) { PgNakshaDictionaries(this) }

    /**
     * Returns the transactions' collection.
     * @return the transactions' collection.
     */
    open fun transactions(): PgNakshaTransactions = getCollection(VIRT_TRANSACTIONS) { PgNakshaTransactions(this) }

    /**
     * Returns the collections' collection.
     * @return the collections' collection.
     */
    open fun collections(): PgNakshaCollections = getCollection(VIRT_COLLECTIONS) { PgNakshaCollections(this) }

    override operator fun get(collectionId: String): PgCollection = getCollection(collectionId) {
        when (collectionId) {
            VIRT_DICTIONARIES -> PgNakshaDictionaries(this)
            VIRT_COLLECTIONS -> PgNakshaCollections(this)
            VIRT_TRANSACTIONS -> PgNakshaTransactions(this)
            else -> PgCollection(this, collectionId)
        }
    }

    override fun get(collectionNumber: Int64): PgCollection? {
        val id = getCollectionId(collectionNumber) ?: return null
        return this[id]
    }

    override fun getCollectionId(collectionNumber: Int64): String? {
        return collectionIdByNumber[collectionNumber] ?: when (collectionNumber) {
            VIRT_TRANSACTIONS_NUMBER -> VIRT_TRANSACTIONS
            VIRT_DICTIONARIES_NUMBER -> VIRT_DICTIONARIES
            VIRT_COLLECTIONS_NUMBER -> VIRT_COLLECTIONS
            else -> null
        }
    }

    /**
     * Returns a shared cached [PgCollection] wrapper. This method is internally called, when a storage or realm are initialized to create all internal collections.
     * @param id the collection identifier.
     * @param constructor the constructor to the collection, if it does not exist already.
     * @return the shared and cached [PgCollection] wrapper.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : PgCollection> getCollection(id: String, constructor: Fn0<T>): T {
        val collections = this.collections
        while (true) {
            var collection: PgCollection? = null
            val existingRef = collections[id]
            if (existingRef != null) collection = existingRef.deref()
            if (collection != null) return collection as T
            collection = constructor.call()
            if (existingRef != null) {
                if (collections.replace(id, existingRef, collection.weakRef)) return collection
                // Conflict, another thread concurrently modified the cache.
            } else {
                collections.putIfAbsent(id, collection.weakRef) ?: return collection
                // Conflict, there is an existing reference, another thread concurrently access the cache.
            }
        }
    }

    /**
     * Returns either the given connection, or opens a new admin connection, when the given connection is _null_.
     */
    private fun connOf(connection: PgConnection?): PgConnection = connection ?: storage.adminConnection(adminOptions()) { _, _ -> }

    /**
     * The counter-part of [connOf], if the connection is _null_, closes [conn], if [commitOnClose] is _true_, commit changes before closing. Does nothing, when the [connection] is not _null_ ([commitOnClose] is ignored in this case).
     */
    private fun closeOf(conn: PgConnection, connection: PgConnection?, commitOnClose: Boolean) {
        if (conn !== connection) {
            if (commitOnClose) conn.commit()
            conn.close()
        }
    }

    /**
     * Refresh the schema information cache.
     *
     * This is automatically called when any value is queries for the first time.
     *
     * @param connection the connection to use to query information from the database; if _null_, a new connection is used temporary.
     * @return this.
     */
    open fun refresh(connection: PgConnection? = null): PgMap {
        if (_updateAt == null || Platform.currentMillis() < _updateAt) {
            logger.info("Refresh map '$id' / schema: '$schemaName' ...")
            val conn = connOf(connection)
            try {
                var cursor = conn.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schemaName)).fetch()
                cursor.use {
                    _oid = cursor["oid"]
                    // TODO: Right now we only support the default map, we need to change this!
                    _number = 0
                }
                cursor = conn.execute("""SELECT oid FROM pg_class WHERE relname='$NAKSHA_COL_SEQ' AND relnamespace=${_oid}""").fetch()
                cursor.use {
                    _collectionNumberSeqOid = cursor["oid"]
                }
            } finally {
                closeOf(conn, connection, false)
            }
            updateUpdateAt()
        }
        return this
    }

    protected fun updateUpdateAt() {
        // TODO: Currently we only support the default map, so there is no need to ever update the cache!
        _updateAt = Platform.currentMillis() + 365 * PlatformUtil.DAY
    }

    /**
     * Tests if the schema exists.
     * @return _true_ if the schema exists.
     */
    override fun exists(): Boolean {
        refresh()
        return _oid != null
    }

    /**
     * Tests if the schema exists.
     * @param connection the connection to use.
     * @return _true_ if the schema exists.
     */
    @JsName("existsUsing")
    fun exists(connection: PgConnection?): Boolean {
        refresh(connection)
        return _oid != null
    }

    // TODO: Implement support for dictionaries using naksha~dictionaries !
    override val dictManager: IDictManager = object : IDictManager {
        override fun putDictionary(dict: JbDictionary) {
            throw NakshaException(NOT_IMPLEMENTED, "putDictionary is not supported by lib-psql yet")
        }

        override fun deleteDictionary(dict: JbDictionary): Boolean {
            throw NakshaException(NOT_IMPLEMENTED, "putDictionary is not supported by lib-psql yet")
        }

        override fun getDictionary(id: String): JbDictionary? = null
    }

    override fun encodingDict(collectionId: String, feature: NakshaFeature?): JbDictionary? = null

    /**
     * Initialize the schema, creating all necessary database tables, installing modules, ....
     *
     * The method does auto-commit, if no [connection] was given; otherwise committing must be done explicitly.
     * @param connection the connection to use to query information from the database; if _null_, a new connection is used temporary.
     */
    open fun init(connection: PgConnection? = null) {
        // Implemented in PsqlMap!
        throw NakshaException(UNSUPPORTED_OPERATION, "This environment does not allow to initialize the schema")
    }

    /**
     * Internally called to initialize the storage.
     * @param storageId the storage-id to install, if _null_, a new storage identifier is generated.
     * @param connection the connection to use, if _null_, a new connection is created.
     * @param version the version of the PLV8 code and PSQL library, if the existing installed version is smaller, it will be updated.
     * @param override if _true_, forcefully override currently installed stored functions and PLV8 modules, even if version matches.
     * @return the storage-id given or the generated storage-id.
     */
    internal open fun init_internal(
        storageId: String?,
        connection: PgConnection,
        version: NakshaVersion = NakshaVersion.latest,
        override: Boolean = false
    ): String {
        // Implemented in PsqlMap!
        throw NakshaException(UNSUPPORTED_OPERATION, "This environment does not allow to initialize the schema")
    }

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
            conn.execute("DROP SCHEMA ${quoteIdent(id)}").close()
        } finally {
            closeOf(conn, connection, true)
        }
    }
}