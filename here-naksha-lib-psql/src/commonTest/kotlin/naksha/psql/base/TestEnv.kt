@file:Suppress("OPT_IN_USAGE")

package naksha.psql.base

import naksha.base.PlatformUtil
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaStorage
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.*
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.test.assertTrue

/**
 * Abstract class for all tests using connection to db. Each test should use an own map.
 */
@Suppress("MemberVisibilityCanBePrivate")
@JsExport
class TestEnv(
    /**
     * The unique map identifier to use for test collections.
     */
    mapId: String? = null,
    /**
     * Delete the map before the test starts?
     */
    deleteMap: Boolean = true,
    /**
     * Enable info-logs before the test starts?
     */
    enableInfoLogs: Boolean = true,
) {
    /**
     * The unique map identifier to use.
     */
    val mapId: String = mapId ?: PgTest.TEST_MAP_ID
    init {
        PlatformUtil.ENABLE_INFO = enableInfoLogs
        NakshaContext.defaultMapId.set(PgTest.TEST_MAP_ID)
        NakshaContext.defaultAppName.set(PgTest.TEST_APP_NAME)
        NakshaContext.defaultAppId.set(PgTest.TEST_APP_ID)
    }

    /**
     * The default [NakshaContext] to be used when opening new PostgresQL sessions via [PgStorage.newWriteSession] or
     * [PgStorage.newReadSession].
     */
    val context = NakshaContext.newInstance(
        appId = PgTest.TEST_APP_ID,
        author = PgTest.TEST_APP_AUTHOR,
        su = true
    ).withMapId(this.mapId).attachToCurrentThread()

    /**
     * The test local storage.
     *
     * **Note**: You can override the docker-config via environment variable `NAKSHA_TEST_PSQL_DB_URL`, for example
     */
    @JvmField
    val storage = Naksha.useStorage(
        NakshaStorage.fromJSON("""{
  "id": "local_psql_test_storage",
  "className": "naksha.psql.PsqlTestStorage"
}""")) as PgStorage

    /**
     * Session options patched for this test environment.
     */
    val options = SessionOptions.from(context)

    fun deleteMap() {
        val request = WriteRequest()
        request.add(Write().deleteMapById(mapId))
        storage.useWriteSession(options) { session ->
            val response = session.execute(request)
            assertTrue { response is SuccessResponse }
        }
    }

    // TODO: Fix me!
    init {
        // if (deleteMap) deleteMap()
    }
}