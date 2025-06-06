@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(ExperimentalJsStatic::class)

package naksha.psql

import naksha.base.AtomicMap
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.objects.NakshaStorage
import naksha.model.request.*
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
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
 * @param mapId The `id` of the map to run all tests in. If `null` is given _(default)_, then [PgTest.TEST_MAP_ID] is used, so a shared map. If an empty string _(`""`)_ is given, then the camel-cased name of this class is used (see [defaultName]).
 */
abstract class PgTestBase(
    collection: NakshaCollection? = NakshaCollection(""),
    mapId: String? = null,
) {
    private var _map: NakshaMap? = null

    /**
     * The selected map, throws an exception, when read before initialized _(the constructor by default initialized this)_.
     */
    val map: NakshaMap
        get() = assertNotNull(_map, "Illegal state, no map used by the test")

    private var _collection: NakshaCollection? = null

    /**
     * The selected collection, throws an exception, when read before initialized _(the constructor by default initialized this)_.
     */
    val collection: NakshaCollection
        get() = assertNotNull(_collection, "Illegal state, no collection used by the test")

    init {
        Naksha.verifyId(defaultName)
        useMap(mapId)
        if (collection != null) {
            useCollection(collection)
        }
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
    private fun ensureMapId(mapId: String?): String = Naksha.verifyId(when (mapId) {
        null -> TEST_MAP_ID
        "" -> defaultName
        else -> mapId
    })

    /**
     * Ensure that the given map is initialized for this test.
     * @param mapId the `id` of the map to use within this test. If `null`, then [PgTest.TEST_MAP_ID] is used, if being an empty string _(`""`)_, then the camel-cased name of the test-class being used.
     * @return the map object.
     */
    fun initMap(mapId: String?): NakshaMap {
        val map_id = ensureMapId(mapId)
        var mapRef = initializedMaps[map_id]
        if (mapRef == null) {
            lock.acquire().use {
                mapRef = initializedMaps[map_id]
                if (mapRef == null) {
                    // Delete the map, should it be still there from previous test runs.
                    dropMap(map_id)

                    // Create the map.
                    var map = NakshaMap(map_id)
                    val request = WriteRequest()
                    request.writes += Write().createMap(map)
                    val response = executeWrite(request)
                    val features = response.features
                    assertEquals(1, features.size)
                    val feature = features.first()
                    map = assertNotNull(feature).proxy(NakshaMap::class)
                    mapRef = MapAndCollections(map)
                    initializedMaps[map_id] = assertNotNull(mapRef)
                    logger.info("Created test map: '$map_id'")
                }
            }
        }
        return assertNotNull(mapRef).map
    }

    /**
     * Initializes the map, and select it, so assign to [map].
     * @param mapId the `id` of the map to initialize and use.
     * @return the map object.
     */
    fun useMap(mapId: String?): NakshaMap {
        val map = initMap(mapId)
        this._map = map
        return map
    }

    fun initCollection(mapId: String, collectionId: String): Pair<NakshaMap, NakshaCollection>
        = initCollection(NakshaCollection(collectionId, mapId))

    fun initCollection(collection: NakshaCollection): Pair<NakshaMap, NakshaCollection> {
        if (collection.id == "") {
            collection.id = defaultName
        } else {
            Naksha.verifyId(collection.id)
        }
        if (collection.mapId == null || collection.mapId == "") {
            collection.mapId = map.id
        } else {
            Naksha.verifyId(collection.mapId)
        }
        val mapId = collection.mapId
        initMap(mapId)
        val mapRef = assertNotNull(initializedMaps[mapId], "The map '$mapId' failed to initialized")
        val colId = collection.id
        var existing = mapRef.collections[colId]
        if (existing == null) {
            lock.acquire().use {
                existing = mapRef.collections[colId]
                if (existing == null) {
                    collection.mapId = mapId
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

    fun useCollection(collectionId: String, mapId: String = map.id): Pair<NakshaMap, NakshaCollection>
            = useCollection(NakshaCollection(collectionId, mapId))

    fun useCollection(collection: NakshaCollection): Pair<NakshaMap, NakshaCollection> {
        val pair = initCollection(collection)
        this._map = pair.first
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
        get() = """SET search_path="${map.id}","naksha~admin",topology,hint_plan,public;"""

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
        }
        assertIs<SuccessResponse>(response, "Response should be 'SuccessResponse', but is '${response::class.simpleName}'")
        return response
    }

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

    protected fun dropMap(mapId: String, options: SessionOptions = newSessionOptions()) {
        val request = WriteRequest()
        request.add(Write().deleteMapById(mapId))
        storage.newWriteSession(options).use { session ->
            val response = session.execute(request)
            assertTrue(response is SuccessResponse, "Failed to drop map with id '$mapId'")
            session.commit()
            initializedMaps.remove(mapId)
        }
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
        val map: NakshaMap,

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
         * The storage configuration used by default.
         */
        @JvmStatic
        @JsStatic
        protected val storageConfig = NakshaStorage.fromJSON("""{
  "id": "local_psql_test_storage",
  "className": "naksha.psql.PsqlTestStorage"
}""").proxy(PgConfig::class)

        /**
         * Create [SessionOptions] and mutate the current [NakshaContext] to actually use the [PgTest] constants for `appName`, `appId`, and `author`, to be used when opening new PostgresQL sessions via [PgStorage.newWriteSession] or [PgStorage.newReadSession].
         * @param appId the `appId`, if modified, otherwise [PgTest.TEST_APP_ID]
         * @param author the `author`, if modified, otherwise [PgTest.TEST_APP_AUTHOR]
         */
        @JvmStatic
        @JsStatic
        fun newSessionOptions(
            appId: String = PgTest.TEST_APP_ID,
            author: String? = PgTest.TEST_APP_AUTHOR,
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
            )
        }

        /**
         * The test storage.
         *
         * **Note**: You can override the docker-config via environment variable `NAKSHA_TEST_PSQL_DB_URL`.
         */
        @JvmField
        val storage = Naksha.useStorage(storageConfig) as PgStorage

        @JvmStatic
        @JsStatic
        fun newWriteSession(): IWriteSession = storage.newWriteSession(newSessionOptions())

        @JvmStatic
        @JsStatic
        fun newReadSession(): IReadSession = storage.newReadSession(newSessionOptions())
    }
}