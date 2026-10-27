package naksha.psql

import naksha.base.Action.Action_C.DELETE
import naksha.base.AtomicMap
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_QUOTED
import naksha.base.Id
import naksha.base.Platform.PlatformCompanion.currentMillis
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.TupleNumber
import naksha.model.Naksha
import naksha.psql.PgColumn.PgColumn_C.FnColumn
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads

/**
 * Internal class to implement a general caching subsystem that allows to cache [PgCatalog] and [PgCollection] using a multi-level cache. It is the base for [PgDatabase] and [PgSession].
 *
 * Actually, if [parent] is not `null`, then this is a sub-cache. This means, it only stores modified catalogs and collections. Loading is only done from [parent] cache. As a session is not visible to the outside world until `commit` is done, this cache should be [flushed][flush] on `commit`, what will copy all catalogs and collections being in this cache into the [parent].
 *
 * If the parent contains an entry for [PgCatalog] or [PgCollection], a read is executed, and that read detects that the cache is stall, so that the reality is a different one _(like a collection does not exist, that the cache believes it exists)_, it will invalidate the corresponding entry from this cache and the [parent], it if is not `null`.
 * @since 3.0
 */
@JsExport
abstract class PgCache protected constructor() {
    /**
     * If not `null`, the parent cache to query catalogs and collections from.
     *
     * If being `null`, then this is the root cache, it will query the database for catalog and collection features.
     * @since 3.0
     */
    protected abstract val parent: PgCache?

    /**
     * The admin-catalog itself _(`naksha~admin`)_.
     * @since 3.0
     */
    abstract val pgAdmin: PgCatalog

    /**
     * The collections' collection _(`naksha~collections`)_ in the admin-schema (`naksha~admin`).
     * @since 3.0
     */
    abstract val pgAdminCollections: PgCollection

    /**
     * The transactions' collection _(`naksha~transactions`)_ in the admin-schema (`naksha~admin`).
     * @since 3.0
     */
    abstract val pgAdminTransactions: PgCollection

    /**
     * The catalogs' collection _(`naksha~catalogs`)_ in the admin-schema (`naksha~admin`).
     * @since 3.0
     */
    abstract val pgAdminCatalogs: PgCollection

    /**
     * The books' collection _(`naksha~books`)_ in the admin-schema (`naksha~admin`).
     * @since 3.0
     */
    abstract val pgAdminBooks: PgCollection

    /**
     * The catalog cache with the key being the catalog-number and the value being the cached [PgCatalog].
     * @since 3.0
     */
    private val catalogCache = AtomicMap<Int, PgCatalog>()

    /**
     * The cache with the root-key being the catalog-number, then the value is another map with the key being the collection-number and the value being the cached [PgCollection].
     * @since 3.0
     */
    private val collectionCache = AtomicMap<Int, AtomicMap<Int, PgCollection>>()

    /**
     * The method does nothing, if [parent] is `null`; otherwise the [PgCatalog] and [PgCollection] entries are removed from this cache and copied into the [parent] cache. This method should be called after a `commit` was successfully done, and only by [PgSession].
     * @since 3.0
     */
    protected fun flush() {
        val parent = this.parent ?: return
        for ((catalogNumber, catalog) in catalogCache) {
            if (catalogCache.remove(catalogNumber, catalog)) {
                parent.cachePgCatalog(catalog)
            }
        }
        for ((_, collections) in collectionCache) {
            for ((collectionNumber, collection) in collections) {
                if (collections.remove(collectionNumber, collection)) {
                    parent.cachePgCollection(collection)
                }
            }
        }
    }

    /**
     * Invalidates the cache for a catalog; does as well invalidate all collections.
     *
     * **This must only be invoked, when the database fails, because a schema _(aka catalog)_ does not exist.**
     *
     * @param catalogNumber the number of the catalog to invalidate its cache entry.
     * @return the catalog that was invalidated; if any.
     * @since 3.0
     * @see PgSession
     */
    internal fun invalidatePgCatalog(catalogNumber: Int): PgCatalog? {
        val parent = this.parent
        if (parent != null) parent.invalidatePgCatalog(catalogNumber)

        // Remove all collections cached for this catalog.
        collectionCache.remove(catalogNumber)
        val catalog = catalogCache[catalogNumber] ?: return null
        return if (catalogCache.remove(catalogNumber, catalog)) catalog else null
    }

    /**
     * Invalidates the cache entry for a collection.
     *
     * **This must only be invoked, when the database fails, because a table _(aka collection)_ does not exist.**
     * @param catalogNumber the number of the catalog in which the collection is located.
     * @param collectionNumber the number of the collection to invalidate its cache entry.
     * @return the collection that was removed; if any.
     * @since 3.0
     */
    internal fun invalidatePgCollection(catalogNumber: Int, collectionNumber: Int): PgCollection? {
        val parent = this.parent
        if (parent != null) parent.invalidatePgCollection(catalogNumber, collectionNumber)

        val collections = collectionCache[catalogNumber] ?: return null
        val collection = collections[collectionNumber] ?: return null
        return if (collections.remove(collectionNumber, collection)) collection else null
    }

    /**
     * Store the given [PgCatalog] into the cache.
     * @param catalog the catalog to store, must have a valid _HEAD_ state **and** must have a valid [TupleNumber].
     * @since 3.0
     */
    internal fun cachePgCatalog(catalog: PgCatalog) {
        val cache = this.catalogCache
        while (true) {
            val id = catalog.id
            val newVersion = catalog.tuple.version

            var existing = cache[id.intValue]
            if (existing == null) {
                existing = cache.putIfAbsent(id.intValue, catalog)
                if (existing == null) break
            }
            if (existing.tuple.action == DELETE) {
                if (cache.replace(id.intValue, existing, catalog)) break
                // Concurrent modification, retry.
                continue
            }
            val existingVersion = existing.tuple.version
            if (existingVersion > newVersion) {
                logger.debug("Do not update catalog '$id', the existing version ($existingVersion) is newer than the new ($newVersion)")
                break
            }
            if (cache.replace(id.intValue, existing, catalog)) break
            // Concurrent modification, retry.
        }
    }

    /**
     * Store the given [PgCollection] into the cache.
     * @param collection the collection to store, must have a valid _HEAD_ state **and** must have a valid [TupleNumber].
     * @since 3.0
     */
    internal fun cachePgCollection(collection: PgCollection) {
        val id = collection.id
        val catalogId = collection.catalogId
        while (true) {
            var cache = collectionCache[catalogId.intValue]
            if (cache == null) {
                cache = AtomicMap()
                val existingMap = collectionCache.putIfAbsent(catalogId.intValue, cache)
                if (existingMap != null) cache = existingMap
            }

            var existing = cache[id.intValue]
            if (existing == null) {
                existing = cache.putIfAbsent(id.intValue, collection) ?: break
                // Concurrent modification.
            }
            if (existing.tuple.action == DELETE) {
                if (cache.replace(id.intValue, existing, collection)) break
                // Concurrent modification, retry.
                continue
            }
            val newVersion = collection.tuple.version
            val existingVersion = existing.tuple.version
            if (existingVersion > newVersion) {
                logger.debug("Skip updating collection '$id', the existing version $existingVersion is greater than the new $newVersion")
                break
            }
            if (cache.replace(id.intValue, existing, collection)) break
            // Concurrent modification, retry.
        }
    }

    /**
     * Returns the existing catalog with the given number; if any.
     * @param number the catalog-number to query.
     * @param conn the connection to use to access the database; `null` if cache-only read.
     * @return the catalog, if it exists; `null` otherwise.
     * @since 3.0
     */
    @JvmOverloads
    fun getPgCatalog(number: Int, conn: PgConnection? = null): PgCatalog? {
        if (number == Id.ADMIN_CATALOG_ID.intValue) return pgAdmin

        // Check local cache, it has priority over parent.
        val catalog = catalogCache[number]
        if (catalog != null) return catalog.readNow()

        // If not in local cache, and we have a parent, load from parent.
        val parent = this.parent
        if (parent != null) return parent.getPgCatalog(number, conn)

        // We are parent (parent==null), query the database.
        if (conn == null) return null
        conn.execute(pgAdmin.SET_searchPath).close()
        val rows = PgRows().withCollection(pgAdminCatalogs)
        val SQL = "SELECT * FROM ${pgAdminCatalogs.quotedName} WHERE fn = $1"
        val plan = conn.prepare(SQL, arrayOf(PgType.INT64.text))
        plan.execute(arrayOf(number)).fetch().use { rows.readAll(it) }
        if (rows.size == 0) return null
        val tuple = rows[0] ?: return null
        Naksha.cache.store(tuple)
        val pgCatalog = PgCatalog(tuple)
        cachePgCatalog(pgCatalog)
        return pgCatalog
    }

    /**
     * Returns the existing collection with the given collection-number; if any.
     * @param collectionNumber the collection-number to query.
     * @param conn the connection to use to access the database; `null` if cache-only access is wanted.
     * @return the collection, if it exists; `null` otherwise.
     * @since 3.0.0
     */
    @JvmOverloads
    fun getPgCollection(catalogNumber: Int, collectionNumber: Int, conn: PgConnection? = null): PgCollection? {
        if (catalogNumber == Id.ADMIN_CATALOG_ID.intValue) {
            when (collectionNumber) {
                Id.COLLECTIONS_COL_ID.intValue -> return pgAdminCollections
                Id.CATALOGS_COL_ID.intValue -> return pgAdminCatalogs
                Id.TRANSACTIONS_COL_ID.intValue -> return pgAdminTransactions
                Id.BOOKS_COL_ID.intValue -> return pgAdminBooks
            }
            return null
        }

        // Find catalog.
        val catalog = getPgCatalog(catalogNumber, conn) ?: return null
        require(catalog.id.intValue == catalogNumber)
        val collectionCache = this.collectionCache
        var collections = collectionCache[catalogNumber]
        val parent = this.parent
        if (collections == null) {
            // If we do not have any collections for this catalog in cache, return from parent cache.
            if (parent != null) return parent.getPgCollection(catalogNumber, collectionNumber, conn)

            // We are root (parent===null), and we know that the catalog exists.
            // Therefore, lets prepare our cache for managing colletions from this catalog.
            collections = AtomicMap()
            val existing = collectionCache.putIfAbsent(catalog.id.intValue, collections)
            if (existing != null) collections = existing
        }

        // At this point we know that collections is a local atomic map for all collections of catalog.

        // Find collection in collections.
        val collection = collections[collectionNumber]
        if (collection != null) return collection.readNow()

        // Collection not in our cache, if we have a parent, redirect to parent.
        if (parent != null) return parent.getPgCollection(catalogNumber, collectionNumber, conn)

        // We are root (parent==null), query the database.
        if (conn == null) return null
        conn.execute(catalog.SET_searchPath).close()
        val SQL = "SELECT * FROM $COLLECTIONS_COL_QUOTED WHERE $FnColumn = $1"
        val plan = conn.prepare(SQL, arrayOf(PgType.INT64.text))
        val rows = PgRows().withCollection(pgAdminCollections)
        plan.execute(arrayOf(collectionNumber.toLong())).fetch().use { rows.readAll(it) }
        if (rows.size == 0) return null
        val tuple = rows[0] ?: return null
        Naksha.cache.store(tuple)
        val pgCollection = PgCollection(catalog.id, tuple)
        cachePgCollection(pgCollection)
        return pgCollection
    }
}
