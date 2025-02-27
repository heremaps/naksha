package naksha.psql.base

import naksha.base.AtomicMap
import naksha.base.AtomicRef
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.request.*
import naksha.psql.PgStorage
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Base class for all tests using postgres as storage. It provides:
 * - safe DB initialization (DB will be spawned once for all tests, not for each!)
 * - safe map initialization (drop, then create map before any test run, only ones)
 * - safe collection initialization (if [collection] is not null, it will be dropped, then created once for the test class)
 * - helper functions for writing and reading to reduce boilerplate
 */
abstract class PgTestBase(private var testCollection: NakshaCollection? = null) {

    val env by lazy {
       TestEnv(deleteMap = true, enableInfoLogs = true)
    }

    val storage: PgStorage
        get() = env.storage

    val map: NakshaMap
        get() = testMap.get() ?: throw IllegalStateException("map not initialized")

    val collection: NakshaCollection
        get() = testCollection ?: throw IllegalStateException("collection not initialized")

    @BeforeTest
    fun ensureCollectionInitialized() {
        if (testMap.get() == null) {
            lock.acquire().use {
                if (testMap.get() == null) {
                    // Delete the map, should it be still there from previous test runs.
                    var request = WriteRequest()
                    request.writes += Write().deleteMapById(env.mapId)
                    executeWrite(request)

                    // Create the map.
                    val newMap = NakshaMap(env.mapId, env.storage.id)
                    request = WriteRequest()
                    request.writes += Write().createMap(newMap)
                    val response = executeWrite(request)
                    assertIs<SuccessResponse>(response)
                    val features = response.features
                    assertEquals(1, features.size)
                    val feature = features.first()
                    assertNotNull(feature)
                    val map = feature.proxy(NakshaMap::class)
                    testMap.set(map)
                }
            }
        }

        val collection = testCollection
        val thisClass = this::class
        if (collection != null && initializedCollections[thisClass] == null) {
            lock.acquire().use {
                if (initializedCollections[thisClass] == null) {
                    val request = WriteRequest()
                    request.writes += Write().createCollection(collection)
                    storage.newWriteSession().use { session ->
                        val response = session.execute(request)
                        require(response is SuccessResponse) {
                            if (response is ErrorResponse) response.error.toString() else "Unknown error"
                        }
                        session.commit()
                    }
                    initializedCollections[thisClass] = collection
                    logger.info("Created test map ${collection.id}")
                }
            }
        }
    }

    protected fun insertFeature(feature: NakshaFeature, sessionOptions: SessionOptions? = null)
        = insertFeatures(listOf(feature), sessionOptions)

    protected fun insertFeatures(vararg features: NakshaFeature)
        = insertFeatures(listOf(*features))

    protected fun insertFeatures(
        features: List<NakshaFeature>,
        sessionOptions: SessionOptions? = null
    ) {
        val writeReq = WriteRequest()
        features.forEach { feature -> writeReq.add(Write().createFeature(collection, feature)) }
        executeWrite(writeReq, sessionOptions)
    }

    protected fun executeWrite(
        request: WriteRequest,
        sessionOptions: SessionOptions? = null
    ): SuccessResponse {
        return env.storage.newWriteSession(sessionOptions).use { session ->
            val response = session.execute(request)
            if (response is ErrorResponse) {
                response.error.print()
            }
            assertIs<SuccessResponse>(response)
            session.commit()
            response
        }
    }

    protected fun executeWriteErrorResponse(
        request: WriteRequest,
        sessionOptions: SessionOptions? = null
    ): ErrorResponse {
        return env.storage.newWriteSession(sessionOptions).use { session ->
            val response = session.execute(request)
            assertIs<ErrorResponse>(response)
            session.commit()
            response
        }
    }

    protected fun executeRead(
        request: ReadRequest,
        sessionOptions: SessionOptions? = null
    ): SuccessResponse {
        return env.storage.newReadSession(sessionOptions).use { session ->
            val response = session.execute(request)
            if (response is ErrorResponse) {
                response.error.print()
            }
            assertIs<SuccessResponse>(response)
            response
        }
    }

    protected fun dropCollection() {
        val collection = initializedCollections.remove(this::class)
        if (collection != null) {
            val deleteCollectionRequest = WriteRequest().add(
                Write().deleteCollectionById(env.mapId, collection.id)
            )
            storage.newWriteSession().use { session ->
                val response = session.execute(deleteCollectionRequest)
                assertIs<SuccessResponse>(response)
                session.commit()
            }
        }
    }

    companion object {
        /**
         * To be held, while initializing a map, to ensure that threads will wait for a collection to be created, before using it.
         */
        private val lock = Platform.newLock()
        private val testMap = AtomicRef<NakshaMap>(null)
        private val initializedCollections = AtomicMap<KClass<out PgTestBase>, NakshaCollection>()
    }
}