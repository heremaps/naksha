package naksha.psql.base

import naksha.base.AtomicMap
import naksha.base.AtomicRef
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.NakshaError
import naksha.model.NakshaException
import naksha.model.SessionOptions
import naksha.model.generalException
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.request.*
import naksha.psql.PgExceptionMapper
import naksha.psql.PgStorage
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.assertIs

/**
 * Base class for all tests using postgres as storage
 * It provides:
 * - safe DB initialization (DB will be spawned once for all tests, not for each!)
 * - safe map initialization (the default map is not null, it will be created once for test class)
 * - safe collection initialization (if [collection] is not null, it will be created once for test class)
 * - helper function for writing and reading to reduce boilerplate
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
                    val request = WriteRequest()
                    val map = NakshaMap(env.mapId, env.storage.id)
                    request.writes += Write().createMap(map)
                    storage.newWriteSession().use { session ->
                        val response = session.execute(request)
                        if (response is ErrorResponse) {
                            if (response.error.code == NakshaError.CONFLICT) {
                                logger.info("Test map ${map.id} exists already")
                            } else {
                                response.error.print()
                                throw NakshaException(response.error)
                            }
                        } else {
                            assertIs<SuccessResponse>(response, "Unknown response type")
                            session.commit()
                            logger.info("Created test map ${map.id}")
                        }
                    }
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
            if (response !is SuccessResponse) {
                if (response is ErrorResponse) throw NakshaException(response.error)
                throw generalException("Unknown response type: ${response::class.qualifiedName}")
            }
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
            assertIs<SuccessResponse>(response)
            response
        }
    }

    protected fun dropCollection() {
        val collection = initializedCollections.remove(this::class)
        if (collection != null) {
            val deleteCollectionRequest = WriteRequest().add(Write().deleteCollectionById(env.mapId, collection.id))
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