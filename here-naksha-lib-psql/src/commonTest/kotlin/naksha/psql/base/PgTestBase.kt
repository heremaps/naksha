package naksha.psql.base

import naksha.base.AtomicInt
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadRequest
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.PgCollection
import naksha.psql.PgConnection
import naksha.psql.PgStorage
import kotlin.test.BeforeTest
import kotlin.test.assertIs

/**
 * Base class for all tests using postgres as storage
 * It provides:
 * - safe DB initialization (DB will be spawned once for all tests, not for each!)
 * - safe collection initialization (if [collection] is not null, it will be created once for test class)
 * - helper function for writing and reading to reduce boilerplate
 */
abstract class PgTestBase(protected val collection: NakshaCollection? = null) {

    private val collectionInitializer: CollectionInitializer?
    protected val env by lazy { commonTestEnv }

    init {
        collectionInitializer = collection?.let { CollectionInitializer(env.storage, collection) }
    }

    protected fun useConnection(): PgConnection =
        env.pgSession.usePgConnection()

    protected fun executeWrite(request: WriteRequest): SuccessResponse {
        return env.storage.newWriteSession().use { session ->
            val response = session.execute(request)
            assertIs<SuccessResponse>(response)
            session.commit()
            response
        }
    }

    protected fun executeRead(request: ReadRequest): SuccessResponse {
        return env.storage.newReadSession().use { session ->
            val response = session.execute(request)
            assertIs<SuccessResponse>(response)
            session.commit()
            response
        }
    }

    @BeforeTest
    fun ensureCollectionInitialized() {
        collectionInitializer?.initializeCollection()
    }

    companion object {
        // This will create a docker, drop maybe existing schema, and initialize the storage.
        private val commonTestEnv: TestEnv by lazy {
            TestEnv(dropSchema = true, initStorage = true, enableInfoLogs = true)
        }
    }
}

private class CollectionInitializer(
    private val storage: PgStorage,
    private val collection: NakshaCollection
) {

    private val collectionInitialized = AtomicInt(NOT_INITIALIZED)

    fun initializeCollection() {
        if (collectionInitialized.compareAndSet(NOT_INITIALIZED, INITIALIZED)) {
            val writeCollectionRequest = WriteRequest()
            writeCollectionRequest.writes += Write().createCollection(null, collection)
            storage.newWriteSession().use { session ->
                val response = session.execute(writeCollectionRequest)
                assertIs<SuccessResponse>(response)
                session.commit()
            }
        }
    }

    companion object {
        private const val NOT_INITIALIZED = 0
        private const val INITIALIZED = 1
    }
}
