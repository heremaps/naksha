package naksha.psql.base

import naksha.base.AtomicMap
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.request.*
import naksha.psql.PgConnection
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
abstract class PgTestBase(internal var collectionField: NakshaCollection? = null) {

    val env by lazy {
       TestEnv(dropSchema = true, enableInfoLogs = true)
    }

    val collection: NakshaCollection
        get() = collectionField ?: throw IllegalStateException("collection not initialized")

    val storage: PgStorage
        get() = env.storage

    protected fun useConnection(): PgConnection =
        env.pgSession.useConnection()

    protected fun insertFeature(feature: NakshaFeature, sessionOptions: SessionOptions? = null) =
        insertFeatures(listOf(feature), sessionOptions)

    protected fun insertFeatures(vararg features: NakshaFeature) =
        insertFeatures(listOf(*features))

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
            assertIs<SuccessResponse>(response)
            response
        }
    }

    protected fun dropCollection() {
        if (initializedCollections.remove(this::class) == true) {
            val deleteCollectionRequest = WriteRequest().add(Write().deleteCollectionById(env.defaultMapId, collection.id))
            storage.newWriteSession().use { session ->
                val response = session.execute(deleteCollectionRequest)
                assertIs<SuccessResponse>(response)
                session.commit()
            }
        }
    }

    @BeforeTest
    fun ensureCollectionInitialized() {
        val collection = collectionField
        if (collection != null && initializedCollections.putIfAbsent(this::class, true) == null) {
            val request = WriteRequest()
            val testMap = NakshaMap(env.storage.id, env.defaultMapId)
            request.writes += Write().createMap(testMap)
            request.writes += Write().createCollection(collection)
            storage.newWriteSession().use { session ->
                val response = session.execute(request)
                assertIs<SuccessResponse>(response)
                session.commit()
            }
        }
    }

    companion object {
        private val initializedCollections = AtomicMap<KClass<out PgTestBase>, Boolean>()
    }
}