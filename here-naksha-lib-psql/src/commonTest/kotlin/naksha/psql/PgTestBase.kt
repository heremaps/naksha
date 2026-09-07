@file:OptIn(ExperimentalJsStatic::class)

package naksha.psql

import naksha.base.AtomicMap
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.base.proxy
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaStorage
import naksha.model.request.*
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass
import kotlin.test.*

/**
 * Base class for all tests using postgres as storage. It provides:
 * - safe DB initialization (DB will be spawned once for all tests, not for each!)
 * - safe map initialization (drop, then create map before any test run, only ones)
 * - safe collection initialization
 * - helper functions for writing and reading to reduce boilerplate
 * @param collection A shared collection to use for all tests. If an empty string _(`""`)_ is used as `id` for the collection _(default)_, then the `id` is changed into to the camel-cased name of this class (see [defaultName]). If explicitly set to `null`, then no shared collection is created.
 * @param catalogId The `id` of the map to run all tests in. If `null` is given _(default)_, then [PgTest.TEST_MAP_ID] is used, so a shared map. If an empty string _(`""`)_ is given, then the camel-cased name of this class is used (see [defaultName]).
 */
abstract class PgTestBase(
    collection: NakshaCollection? = NakshaCollection(""),
    catalogId: String? = null,
) {
    private var _catalog: NakshaCatalog? = null

    /**
     * The selected map, throws an exception, when read before initialized _(the constructor by default initialized this)_.
     */
    val catalog: NakshaCatalog
        get() = assertNotNull(_catalog, "Illegal state, no catalog used by the test")

    private var _collection: NakshaCollection? = null

    /**
     * The selected collection, throws an exception, when read before initialized _(the constructor by default initialized this)_.
     */
    val collection: NakshaCollection
        get() = assertNotNull(_collection, "Illegal state, no collection used by the test")

    init {
        NakshaIdType.COLLECTION.verify(defaultName)
        useCatalog(catalogId)
        if (collection != null) {
            useCollection(collection)
        }
        //PlatformUtil.ENABLE_INFO = true
        //Naksha.DEFAULT_SESSION_LOG_LEVEL = PgLogLevel.EXPLAIN_AND_QUERIES
    }

    private var _defaultName: String? = null

    /**
     * The default collection name of this test class.
     * @return default collection name of this test class.
     */
    val defaultName: String
        get() {
            var name = _defaultName
            if (name == null) {
                name = camelCase(this::class)
                _defaultName = name
            }
            return name
        }

    @Suppress("DEPRECATION")
    private fun ensureCatalogId(catalogId: String?): String = NakshaIdType.CATALOG.verify(when (catalogId) {
        null -> TEST_MAP_ID
        "" -> TEST_MAP_ID
        else -> catalogId
    })

    /**
     * Ensure that the given map is initialized for this test.
     * @param mapId the `id` of the map to use within this test. If `null`, then [PgTest.TEST_MAP_ID] is used, if being an empty string _(`""`)_, then the camel-cased name of the test-class being used.
     * @return the map object.
     */
    fun initCatalog(mapId: String?): NakshaCatalog {
        val catalog_id = ensureCatalogId(mapId)
        var catalogRef = initializedMaps[catalog_id]
        if (catalogRef == null) {
            lock.acquire().use {
                catalogRef = initializedMaps[catalog_id]
                if (catalogRef == null) {
                    // Remove any stale registry entry left over from a previous run.
                    // The schema itself was already dropped by cleanDatabase() at start-up, but
                    // the naksha~admin catalogue may still hold a row for this map.
                    dropCatalog(catalog_id)

                    // Create the map.
                    var catalog = NakshaCatalog(catalog_id)
                    val request = WriteRequest()
                    request.writes += Write().createMap(catalog)
                    val response = executeWrite(request)
                    val features = response.features
                    assertEquals(1, features.size)
                    val feature = features.first()
                    catalog = assertNotNull(feature).proxy(NakshaCatalog::class)
                    catalogRef = MapAndCollections(catalog)
                    initializedMaps[catalog_id] = assertNotNull(catalogRef)
                    logger.info("Created test map: '$catalog_id'")
                }
            }
        }
        return assertNotNull(catalogRef).map
    }

    /**
     * Initializes the map, and select it, so assign to [catalog].
     * @param catalogId the `id` of the map to initialize and use.
     * @return the map object.
     */
    fun useCatalog(catalogId: String?): NakshaCatalog {
        val catalog = initCatalog(catalogId)
        this._catalog = catalog
        return catalog
    }

    fun initCollection(catalogId: String, collectionId: String): Pair<NakshaCatalog, NakshaCollection>
        = initCollection(NakshaCollection(collectionId, catalogId))

    fun initCollection(collection: NakshaCollection): Pair<NakshaCatalog, NakshaCollection> {
        if (collection.id == "") {
            collection.id = defaultName
        } else {
            NakshaIdType.COLLECTION.verify(collection.id)
        }
        if (collection.catalogId == null || collection.catalogId == "") {
            collection.catalogId = catalog.id
        } else {
            NakshaIdType.CATALOG.verify(collection.catalogId)
        }
        val catalogId = collection.catalogId
        initCatalog(catalogId)
        val mapRef = assertNotNull(initializedMaps[catalogId], "The map '$catalogId' failed to initialized")
        val colId = collection.id
        var existing = mapRef.collections[colId]
        if (existing == null) {
            lock.acquire().use {
                existing = mapRef.collections[colId]
                if (existing == null) {
                    collection.catalogId = catalogId
                    val request = WriteRequest()
                    request.writes += Write().createCollection(collection)
                    storage.newWriteSession(newSessionOptions()).use { session ->
                        val response = assertSuccess(session.execute(request))
                        session.commit()
                        val features = response.features
                        assertEquals(1, features.size)
                        val feature = features[0]
                        existing = feature.proxy(NakshaCollection::class)
                        mapRef.collections[colId] = assertNotNull(existing)
                    }
                    logger.info("Created test collection: '${colId}'")
                }
            }
        }
        return Pair(mapRef.map, assertNotNull(existing))
    }

    fun useCollection(collectionId: String, mapId: String = catalog.id): Pair<NakshaCatalog, NakshaCollection>
            = useCollection(NakshaCollection(collectionId, mapId))

    fun useCollection(collection: NakshaCollection): Pair<NakshaCatalog, NakshaCollection> {
        val pair = initCollection(collection)
        this._catalog = pair.first
        this._collection = pair.second
        return pair
    }

    fun testWithCollection(testName: String) {
        useCollection(camelCase(testName))
    }

    /**
     * The SQL query to be focus on the current map:
     * ```
     * SET search_path="$TEST_MAP_ID","naksha~admin",topology,hint_plan,public;
     * ```
     */
    val SET_SEARTH_PATH_SQL
        get() = """SET search_path="${catalog.id}","naksha~admin",topology,hint_plan,public;"""

    protected fun insertFeature(
        feature: NakshaFeature,
        sessionOptions: SessionOptions? = newSessionOptions()
    ): SuccessResponse = insertFeatures(listOf(feature), sessionOptions)

    protected fun insertFeatures(vararg features: NakshaFeature): SuccessResponse =
        insertFeatures(listOf(*features))

    protected fun insertFeatures(
        features: List<NakshaFeature>,
        sessionOptions: SessionOptions? = newSessionOptions()
    ): SuccessResponse {
        val writeReq = WriteRequest()
        features.forEach { feature -> writeReq.add(Write().createFeature(collection, feature)) }
        return executeWrite(writeReq, sessionOptions)
    }

    protected fun assertSuccess(response: Response): SuccessResponse {
        if (response is ErrorResponse) {
            response.error.print(logger)
            fail("Expected SuccessResponse but got ErrorResponse: code=${response.error.code}, msg=${response.error.msg}")
        }
        assertIs<SuccessResponse>(response, "Response should be 'SuccessResponse', but is '${response::class.simpleName}'")
        return response
    }

    @JvmOverloads
    protected fun executeWrite(
        request: WriteRequest,
        sessionOptions: SessionOptions? = newSessionOptions()
    ): SuccessResponse = storage.newWriteSession(sessionOptions).use { session ->
        val response = assertSuccess(session.execute(request))
        val start = Platform.currentNanos()
        session.commit()
        val end = Platform.currentNanos()
        val millis = (end.toDouble() - start.toDouble()) / 1e6
        logger.info("Commit took $millis millis")
        response
    }

    protected fun executeWriteErrorResponse(
        request: WriteRequest,
        sessionOptions: SessionOptions? = newSessionOptions()
    ): ErrorResponse {
        return storage.newWriteSession(sessionOptions).use { session ->
            val response = session.execute(request)
            assertIs<ErrorResponse>(response)
            session.commit()
            response
        }
    }

    protected fun executeRead(
        request: ReadRequest,
        sessionOptions: SessionOptions? = newSessionOptions()
    ): SuccessResponse {
        return storage.newReadSession(sessionOptions).use { session ->
            val response = session.execute(request)
            if (response is ErrorResponse) {
                response.error.print(logger)
                fail("ErrorResponse while reading: ${response.error.code}: \n${response.error.msg}")
            }
            assertIs<SuccessResponse>(response)
            response
        }
    }

    protected fun dropCatalog(mapId: String, options: SessionOptions = newSessionOptions()) {
        val request = WriteRequest()
        request.add(Write().deleteMapById(mapId))
        storage.newWriteSession(options).use { session ->
            val response = session.execute(request)
            if (response is ErrorResponse) {
                response.error.print(logger)
                fail("dropMap('$mapId') failed: ${response.error.code}: ${response.error.msg}")
            }
            session.commit()
        }
        initializedMaps.remove(mapId)
    }

    protected fun dropCollection(mapId: String, collectionId: String, options: SessionOptions = newSessionOptions()) {
        val map = initializedMaps[mapId]
        val collection = map?.collections?.get(collectionId)
        val deleteCollectionRequest = WriteRequest().add(
            Write().deleteCollectionById(mapId, collectionId)
        )
        storage.newWriteSession(options).use { session ->
            val response = session.execute(deleteCollectionRequest)
            assertTrue(response is SuccessResponse, "Failed to drop collection with id '$collectionId'")
            session.commit()
            if (map != null && collection != null) {
                map.collections.remove(collectionId, collection)
            }
        }
    }

    private data class MapAndCollections(
        /**
         * The map.
         */
        val map: NakshaCatalog,

        /**
         * All collections in the map, created for the tests by `id`.
         */
        val collections: AtomicMap<String, NakshaCollection> = AtomicMap()
    )

    companion object {
        init {
            PlatformUtil.ENABLE_INFO = true
            NakshaContext.defaultAppName.set(PgTest.TEST_APP_NAME)
            NakshaContext.defaultAppId.set(PgTest.TEST_APP_ID)
        }

        /**
         * The storage configuration used by default.
         */
        @JvmStatic
        @JsStatic
        val storageConfig = NakshaStorage.fromJSON("""{
  "id": "${Platform.getTestStorageId()}",
  "className": "naksha.psql.PsqlTestStorage"
}""").proxy(PgConfig::class)

        /**
         * The test storage.
         *
         * **Note**: You can override the docker-config via environment variable `NAKSHA_TEST_PSQL_DB_URL`.
         */
        @JvmField
        val storage = Naksha.useStorage(storageConfig) as PgStorage

        init {
            cleanDatabase()
        }

        /**
         * Drop all test map schemas so each test run starts from a blank slate.
         *
         * Only schemas that are known test artefacts ([TestMap]) are removed — nothing else is
         * touched.  `naksha~admin` is intentionally left alone so that the Naksha storage stays
         * fully operational; stale map entries in its registry are cleaned up per-map via the
         * normal [dropCatalog] call inside [initCatalog].
         *
         * The function is idempotent: `DROP SCHEMA IF EXISTS … CASCADE` is safe when the schemas
         * do not exist (first ever run).
         */
        private fun cleanDatabase() {
            storage.adminConnection().use { conn ->
                for (tm in TestMap.entries) {
                    conn.execute("""DROP SCHEMA IF EXISTS "${tm.id}" CASCADE""").close()
                }
                logger.info("Test database cleaned: dropped ${TestMap.entries.size} test schema(s)")
            }
        }

        fun camelCase(simpleName: String): String {
            val colName = StringBuilder()
            for (c in simpleName) {
                if (c.isUpperCase()) {
                    if (colName.isNotEmpty()) colName.append("_")
                    colName.append(c.lowercase())
                } else {
                    colName.append(c)
                }
            }
            return colName.toString()
        }

        fun <T : PgTestBase> camelCase(klass: KClass<out T>): String
            = camelCase(assertNotNull(klass.simpleName, "Missing simpleName for test class"))

        /**
         * To be held, while initializing a map or collection, to prevent concurrent creation of the same maps and/or collections.
         */
        private val lock = Platform.newLock()

        /**
         * All maps created for the tests by `id`.
         */
        private val initializedMaps = AtomicMap<String, MapAndCollections>()

        /**
         * Create [SessionOptions] and mutate the current [NakshaContext] to actually use the [PgTest] constants for `appName`, `appId`, and `author`, to be used when opening new PostgresQL sessions via [PgStorage.newWriteSession] or [PgStorage.newReadSession].
         * @param appId the `appId`, if modified, otherwise [PgTest.TEST_APP_ID]
         * @param author the `author`, if modified, otherwise [PgTest.TEST_APP_AUTHOR]
         * @param logLevel the `logLevel`, if modified, otherwise [Naksha.DEFAULT_SESSION_LOG_LEVEL]
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun newSessionOptions(
            appId: String = PgTest.TEST_APP_ID,
            author: String? = PgTest.TEST_APP_AUTHOR,
            logLevel: String? = Naksha.DEFAULT_SESSION_LOG_LEVEL
        ): SessionOptions {
            val context = NakshaContext.currentContext()
            context.appName = PgTest.TEST_APP_NAME
            context.appId = appId
            context.author = author
            return SessionOptions(
                appName = PgTest.TEST_APP_NAME,
                appId = appId,
                author = author,
                useMaster = true,
                logLevel = logLevel,
            )
        }

        @JvmStatic
        @JsStatic
        fun newWriteSession(): IWriteSession = storage.newWriteSession(newSessionOptions())

        @JvmStatic
        @JsStatic
        fun newReadSession(): IReadSession = storage.newReadSession(newSessionOptions())
    }
}