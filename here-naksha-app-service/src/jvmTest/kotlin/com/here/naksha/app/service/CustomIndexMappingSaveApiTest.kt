@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.app.service

import com.here.naksha.app.common.*
import naksha.base.NakshaError
import naksha.base.JvmBoxingUtil
import naksha.base.Platform
import com.here.naksha.lib.core.HubInternalIdentifiers.EVENT_HANDLERS
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.request.*
import naksha.model.util.CustomStoragePropertiesUtil
import org.json.JSONObject
import org.json.JSONArray
import org.junit.jupiter.api.BeforeEach
import naksha.model.objects.NakshaCatalog
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.http.HttpResponse
import java.util.UUID

/** Save-time creation and verification of mapped Handler collections. */
class CustomIndexMappingSaveApiTest : ApiTest() {
    private val stream = UUID.randomUUID().toString()
    @BeforeEach fun ensureDisposableCatalog(@NakshaAppInjection app: NakshaApp) {
        NakshaContext.newInstance("custom-index-fixture", "test", null, true).attachToCurrentThread()
        val storageId = handler("unused", "unused").getJSONObject("properties").getString("storageId")
        val storage = app.hub.getStorageById(storageId)
        val map = CustomStoragePropertiesUtil.getSchema(storage.config!!)
        storage.useWriteSession(SessionOptions()) { writer ->
            val result = writer.execute(WriteRequest().add(Write().upsertMap(NakshaCatalog(requireNotNull(map)), false)))
            assertTrue(result is SuccessResponse, result.toString())
            writer.commit()
        }
    }
    private fun handler(id: String, collection: String, mapped: Boolean = true) =
        JSONObject(TestUtil.loadFileOrFail("CustomIndexMapping/TC02_handlerMappingLifecycle/create_event_handler.json")).apply {
            put("id", id)
            getJSONObject("properties").getJSONObject("collection").put("id", collection)
            if (!mapped) getJSONObject("properties").getJSONObject("collection").remove("customIndexMapping")
        }
    private fun space(id: String, handler: String) = JSONObject().put("id", id).put("type", "Space")
        .put("title", "Custom index space").put("description", "Custom index save test")
        .put("eventHandlerIds", JSONArray().put(handler)).put("properties", JSONObject())
    private fun status(r: HttpResponse<String>, expected: Int = 200) = assertEquals(expected, r.statusCode(), r.body())
    private fun storedCollection(app: NakshaApp, storageId: String, collection: String): List<String> {
        NakshaContext.newInstance("custom-index-probe", "test", null, true).attachToCurrentThread()
        val storage = app.hub.getStorageById(storageId)
        val map = CustomStoragePropertiesUtil.getSchema(storage.config!!)
        val response = storage.useReadSession(SessionOptions()) { reader ->
            reader.execute(ReadCollections().withMapId(map).addCollectionId(collection))
        }
        if (response is ErrorResponse && response.error.code == NakshaError.COLLECTION_NOT_FOUND) return emptyList()
        assertTrue(response is SuccessResponse, response.toString())
        return (response as SuccessResponse).features.map { it!!.id }
    }
    @Test fun rejectedReadOnlyHandlerSaveMustNotCreateCollection(@NakshaAppInjection app: NakshaApp) {
        val h = handler("mapping_save_forbidden_handler", "mapping_save_forbidden_collection")
        val storageId = h.getJSONObject("properties").getString("storageId")
        assertTrue(storedCollection(app, storageId, "mapping_save_forbidden_collection").isEmpty())
        val r = nakshaClient.post("hub/handlers", h.toString(), stream, "Bearer " + TestUtil.readOnlyJwt())
        status(r, 403)
        val after = storedCollection(app, storageId, "mapping_save_forbidden_collection")
        assertTrue(after.isEmpty(), "403 must not create a physical collection")
    }
    @Test fun customHandlerMustNotProvisionArbitraryCollectionProperty(@NakshaAppInjection app: NakshaApp) {
        val h = handler("mapping_save_custom_handler", "mapping_save_custom_collection")
        h.put("className", "com.example.CustomHandler")
        val storageId = h.getJSONObject("properties").getString("storageId")
        status(nakshaClient.post("hub/handlers", h.toString(), stream))
        val after = storedCollection(app, storageId, "mapping_save_custom_collection")
        assertTrue(after.isEmpty(), "Unknown handler properties are not a storage contract")
    }
    @Test fun disablingAutoCreateMustNotAllowTypeChange() {
        val id = "mapping_save_autocreate_handler"
        val h = handler(id, "mapping_save_autocreate_collection")
        status(nakshaClient.post("hub/handlers", h.toString(), stream))
        status(nakshaClient.post("hub/spaces", space(id + "_space", id).toString(), stream))
        val saved = JSONObject(nakshaClient.get("hub/handlers/$id", stream).body())
        val p = saved.getJSONObject("properties")
        p.put("autoCreateCollection", false)
        val mapping = p.getJSONObject("collection").getJSONArray("customIndexMapping")
        for (i in 0 until mapping.length()) mapping.getJSONObject(i).put("dataType", "string")
        val r = nakshaClient.put("hub/handlers/$id", saved.toString(), stream)
        status(r, 409)
    }
    @Test fun removingAllMappingsMustNotBypassHandlerVerification() {
        val id = "mapping_save_removed_mapping_handler"
        val h = handler(id, "mapping_save_removed_mapping_collection")
        status(nakshaClient.post("hub/handlers", h.toString(), stream))
        status(nakshaClient.post("hub/spaces", space(id + "_space", id).toString(), stream))
        val saved = JSONObject(nakshaClient.get("hub/handlers/$id", stream).body())
        saved.getJSONObject("properties").getJSONObject("collection").remove("customIndexMapping")
        val r = nakshaClient.put("hub/handlers/$id", saved.toString(), stream)
        status(r, 409)
    }
    private fun saveDirect(app: NakshaApp, upsert: Boolean, vararg json: JSONObject) {
        NakshaContext.newInstance("custom-index-direct-save", "test", null, true).attachToCurrentThread()
        val request = WriteRequest()
        for (objectJson in json) {
            val config = requireNotNull(JvmBoxingUtil.box(Platform.fromJSON(objectJson.toString()), EventHandlerConfig::class.java))
            request.add(if (upsert) Write().upsertFeature(app.hub.adminMapId, EVENT_HANDLERS, config)
                else Write().createFeature(app.hub.adminMapId, EVENT_HANDLERS, config))
        }
        val result = app.hub.spaceStorage.useWriteSession(SessionOptions()) { it.execute(request) }
        assertTrue(result is SuccessResponse, result.toString())
    }
    @Test fun upsertMustProvisionMappedHandler(@NakshaAppInjection app: NakshaApp) {
        val h = handler("mapping_save_upsert_handler", "mapping_save_upsert_collection")
        saveDirect(app, true, h)
        val after = storedCollection(app, h.getJSONObject("properties").getString("storageId"), "mapping_save_upsert_collection")
        assertFalse(after.isEmpty(), "A mapped Handler saved with UPSERT must provision its collection")
    }
    @Test fun batchCreateMustProvisionMappedHandlers(@NakshaAppInjection app: NakshaApp) {
        val h1 = handler("mapping_save_batch_handler_one", "mapping_save_batch_collection_one")
        val h2 = handler("mapping_save_batch_handler_two", "mapping_save_batch_collection_two")
        saveDirect(app, false, h1, h2)
        val after = storedCollection(app, h1.getJSONObject("properties").getString("storageId"), "mapping_save_batch_collection_one")
        assertFalse(after.isEmpty(), "A mapped Handler saved in a batch must provision its collection")
    }

    @Test fun rejectedReadOnlySpaceSaveMustNotCreateCollection(@NakshaAppInjection app: NakshaApp) {
        val id = "mapping_save_forbidden_space_handler"
        val h = handler(id, "unused", false)
        h.getJSONObject("properties").remove("collection")
        val storageId = h.getJSONObject("properties").getString("storageId")
        status(nakshaClient.post("hub/handlers", h.toString(), stream))
        val collection = "mapping_save_forbidden_space_table"
        val s = JSONObject(TestUtil.loadFileOrFail("CustomIndexMapping/TC01_spaceMappingLifecycle/create_space.json"))
            .put("id", "mapping_save_forbidden_space")
            .put("eventHandlerIds", JSONArray().put(id))
        s.getJSONObject("properties").getJSONObject("collection").put("id", collection)
        status(nakshaClient.post("hub/spaces", s.toString(), stream, "Bearer " + TestUtil.readOnlyJwt()), 403)
        assertTrue(storedCollection(app, storageId, collection).isEmpty(), "403 must not create a physical collection")
    }

    private fun multiCollectionHandler(id: String, vararg collections: Pair<String, String>): JSONObject {
        val h = handler(id, "unused")
        val properties = h.getJSONObject("properties")
        properties.remove("collection")
        properties.put("collections", JSONArray().apply {
            for ((collection, type) in collections) put(JSONObject().put("id", collection).put("customIndexMapping",
                JSONArray().put(JSONObject().put("jsonPath", JSONArray().put("properties").put("score")).put("dataType", type))))
        })
        return h.put("className", MultiCollectionStorageHandler::class.java.name)
    }

    @Test fun subclassCollectionsAreCreatedOnHandlerSave(@NakshaAppInjection app: NakshaApp) {
        val h = multiCollectionHandler("mapping_save_multi_handler",
            "mapping_save_multi_one" to "int32", "mapping_save_multi_two" to "string")
        val storageId = h.getJSONObject("properties").getString("storageId")
        status(nakshaClient.post("hub/handlers", h.toString(), stream))
        assertFalse(storedCollection(app, storageId, "mapping_save_multi_one").isEmpty())
        assertFalse(storedCollection(app, storageId, "mapping_save_multi_two").isEmpty())
    }

    @Test fun subclassWithConflictingCollectionDefinitionsIsRejected() {
        val h = multiCollectionHandler("mapping_save_multi_conflict_handler",
            "mapping_save_multi_conflict" to "int32", "mapping_save_multi_conflict" to "string")
        status(nakshaClient.post("hub/handlers", h.toString(), stream), 409)
        status(nakshaClient.get("hub/handlers/mapping_save_multi_conflict_handler", stream), 404)
    }
}
