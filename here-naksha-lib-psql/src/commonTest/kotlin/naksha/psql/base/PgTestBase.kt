package naksha.psql.base

import naksha.base.AtomicInt
import naksha.base.AtomicMap
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadRequest
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.PgConnection
import naksha.psql.PgStorage
import naksha.psql.base.CollectionsInitializer.dropCollectionFor
import naksha.psql.base.CollectionsInitializer.initializeCollectionFor
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.assertIs

/**
 * Base class for all tests using postgres as storage
 * It provides:
 * - safe DB initialization (DB will be spawned once for all tests, not for each!)
 * - safe collection initialization (if [collection] is not null, it will be created once for test class)
 * - helper function for writing and reading to reduce boilerplate
 */
abstract class PgTestBase(val collection: NakshaCollection? = null) {

    protected val env by lazy { commonTestEnv }

    val storage: PgStorage
        get() = env.storage

    protected fun useConnection(): PgConnection =
        env.pgSession.usePgConnection()

    protected fun executeWrite(request: WriteRequest, sessionOptions: SessionOptions? = null): SuccessResponse {
        return env.storage.newWriteSession(sessionOptions).use { session ->
            val response = session.execute(request)
            assertIs<SuccessResponse>(response)
            session.commit()
            response
        }
    }

    protected fun executeRead(request: ReadRequest, sessionOptions: SessionOptions? = null): SuccessResponse {
        return env.storage.newReadSession(sessionOptions).use { session ->
            val response = session.execute(request)
            assertIs<SuccessResponse>(response)
            session.commit()
            response
        }
    }

    protected fun dropCollection() {
        dropCollectionFor(this)
    }

    @BeforeTest
    fun ensureCollectionInitialized() {
        initializeCollectionFor(this)
    }

    companion object {
        // This will create a docker, drop maybe existing schema, and initialize the storage.
        private val commonTestEnv: TestEnv by lazy {
            TestEnv(dropSchema = true, initStorage = true, enableInfoLogs = true)
        }
    }
}

private object CollectionsInitializer {

    private val initializedCollections = AtomicMap<KClass<out PgTestBase>, AtomicInt>()
    private const val NOT_INITIALIZED = 0
    private const val INITIALIZED = 1

    fun <T : PgTestBase> initializeCollectionFor(testSuite: T) {
        if (testSuite.collection == null) return
        val initialized =
            initializedCollections.putIfAbsent(testSuite::class, AtomicInt(NOT_INITIALIZED))
                ?: initializedCollections[testSuite::class]!!
        if (initialized.compareAndSet(NOT_INITIALIZED, INITIALIZED)) {
            val writeCollectionRequest = WriteRequest()
            writeCollectionRequest.writes += Write().createCollection(null, testSuite.collection)
            testSuite.storage.newWriteSession().use { session ->
                val response = session.execute(writeCollectionRequest)
                assertIs<SuccessResponse>(response)
                session.commit()
            }
        }
    }

    fun <T: PgTestBase> dropCollectionFor(testSuite: T){
        require(testSuite.collection != null)
        val deleteCollectionRequest = WriteRequest().add(
            Write().deleteCollectionById(
                map = null,
                collectionId = testSuite.collection.id
            )
        )
        testSuite.storage.newWriteSession().use { session ->
            val response = session.execute(deleteCollectionRequest)
            assertIs<SuccessResponse>(response)
            session.commit()
        }
        initializedCollections.remove(testSuite::class)
    }
}
