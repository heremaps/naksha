package com.here.naksha.lib.hub

import naksha.model.*
import naksha.model.objects.*
import naksha.model.request.*
import naksha.psql.PsqlStorage
import kotlin.test.*

/** Disposable PsqlTestStorage only. No local developer database configuration. */
abstract class CustomIndexNativeFixture {
    val storage: PsqlStorage get() = backend
    val catalog: NakshaCatalog get() = testCatalog
    init { NakshaContext.newInstance("custom-index-native-tests", "test", null, true).attachToCurrentThread() }
    fun newSessionOptions() = SessionOptions()
    fun executeWrite(request: WriteRequest): SuccessResponse = storage.newWriteSession().use { writer ->
        val response = writer.execute(request)
        if (response is ErrorResponse) fail(response.error.toString())
        writer.commit()
        assertIs<SuccessResponse>(response)
    }
    fun executeRead(request: ReadRequest): SuccessResponse = storage.newReadSession().use { reader ->
        val response = reader.execute(request)
        if (response is ErrorResponse) fail(response.error.toString())
        assertIs<SuccessResponse>(response)
    }
    companion object {
        private val backend: PsqlStorage by lazy {
            require(System.getenv("NAKSHA_TEST_PSQL_DB_URL").isNullOrBlank()) { "Use a disposable test container" }
            require(System.getProperty("naksha.test.psql.db.url").isNullOrBlank()) { "Use a disposable test container" }
            Naksha.useStorage(NakshaStorage.fromJSON("""{"id":"custom_index_hub_storage","className":"naksha.psql.PsqlTestStorage","properties":{"schema":"custom_index_hub"}}""")) as PsqlStorage
        }
        private val testCatalog: NakshaCatalog by lazy {
            val catalog = NakshaCatalog("custom_index_native")
            backend.newWriteSession().use { writer ->
                val response = writer.execute(WriteRequest().add(Write().createMap(catalog)))
                if (response is ErrorResponse) fail(response.error.toString())
                writer.commit()
            }
            catalog
        }
    }
}
