package com.here.naksha.app.service;

import static com.here.naksha.lib.core.HubInternalIdentifiers.EVENT_HANDLERS;
import static org.junit.jupiter.api.Assertions.*;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaAppInjection;
import com.here.naksha.app.common.TestUtil;
import com.here.naksha.app.service.testutil.MultiCollectionStorageHandler;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import naksha.base.NakshaError;
import naksha.base.Platform;
import naksha.model.IStorage;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCatalog;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import naksha.model.util.CustomStoragePropertiesUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Save-time creation and verification of mapped Handler collections. */
class CustomIndexMappingSaveApiTest extends ApiTest {
  private final String stream = UUID.randomUUID().toString();

  @BeforeEach void ensureDisposableCatalog(@NakshaAppInjection NakshaApp app) throws JSONException {
    NakshaContext.newInstance("custom-index-fixture", "test", null, true).attachToCurrentThread();
    String storageId = handler("unused", "unused").getJSONObject("properties").getString("storageId");
    IStorage storage = app.getHub().getStorageById(storageId);
    String map = CustomStoragePropertiesUtil.getSchema(Objects.requireNonNull(storage.getConfig()));
    storage.useWriteSession(new SessionOptions(), writer -> {
      Response result = writer.execute(new WriteRequest().add(new Write().upsertMap(new NakshaCatalog(Objects.requireNonNull(map)), false)));
      assertTrue(result instanceof SuccessResponse, result.toString());
      writer.commit();
      return null;
    });
  }

  private JSONObject handler(String id, String collection) throws JSONException {
    return handler(id, collection, true);
  }

  private JSONObject handler(String id, String collection, boolean mapped) throws JSONException {
    JSONObject handler = new JSONObject(TestUtil.loadFileOrFail("CustomIndexMapping/TC02_handlerMappingLifecycle/create_event_handler.json"));
    handler.put("id", id);
    JSONObject definition = handler.getJSONObject("properties").getJSONObject("collection");
    definition.put("id", collection);
    if (!mapped) definition.remove("customIndexMapping");
    return handler;
  }

  private JSONObject space(String id, String handler) throws JSONException {
    return new JSONObject().put("id", id).put("type", "Space")
        .put("title", "Custom index space").put("description", "Custom index save test")
        .put("eventHandlerIds", new JSONArray().put(handler)).put("properties", new JSONObject());
  }

  private void status(HttpResponse<String> response) {
    status(response, 200);
  }

  private void status(HttpResponse<String> response, int expected) {
    assertEquals(expected, response.statusCode(), response.body());
  }

  private List<String> storedCollection(NakshaApp app, String storageId, String collection) {
    NakshaContext.newInstance("custom-index-probe", "test", null, true).attachToCurrentThread();
    IStorage storage = app.getHub().getStorageById(storageId);
    String map = CustomStoragePropertiesUtil.getSchema(Objects.requireNonNull(storage.getConfig()));
    Response response = storage.useReadSession(new SessionOptions(), reader ->
        reader.execute(new ReadCollections().withMapId(map).addCollectionId(collection)));
    if (response instanceof ErrorResponse && NakshaError.COLLECTION_NOT_FOUND.equals(((ErrorResponse) response).getError().getCode())) {
      return List.of();
    }
    SuccessResponse success = assertInstanceOf(SuccessResponse.class, response, response.toString());
    return success.getFeatures().stream().map(NakshaFeature::getId).collect(Collectors.toList());
  }

  @Test void rejectedReadOnlyHandlerSaveMustNotCreateCollection(@NakshaAppInjection NakshaApp app) throws Exception {
    JSONObject h = handler("mapping_save_forbidden_handler", "mapping_save_forbidden_collection");
    String storageId = h.getJSONObject("properties").getString("storageId");
    assertTrue(storedCollection(app, storageId, "mapping_save_forbidden_collection").isEmpty());
    status(getNakshaClient().post("hub/handlers", h.toString(), stream, "Bearer " + TestUtil.readOnlyJwt()), 403);
    assertTrue(storedCollection(app, storageId, "mapping_save_forbidden_collection").isEmpty(), "403 must not create a physical collection");
  }

  @Test void customHandlerMustNotProvisionArbitraryCollectionProperty(@NakshaAppInjection NakshaApp app) throws Exception {
    JSONObject h = handler("mapping_save_custom_handler", "mapping_save_custom_collection");
    h.put("className", "com.example.CustomHandler");
    String storageId = h.getJSONObject("properties").getString("storageId");
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    assertTrue(storedCollection(app, storageId, "mapping_save_custom_collection").isEmpty(), "Unknown handler properties are not a storage contract");
  }

  @Test void disablingAutoCreateForMappedHandlerIsRejected() throws Exception {
    String id = "mapping_save_autocreate_handler";
    JSONObject h = handler(id, "mapping_save_autocreate_collection");
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    status(getNakshaClient().post("hub/spaces", space(id + "_space", id).toString(), stream));
    JSONObject saved = new JSONObject(getNakshaClient().get("hub/handlers/" + id, stream).body());
    JSONObject properties = saved.getJSONObject("properties");
    properties.put("autoCreateCollection", false);
    JSONArray mapping = properties.getJSONObject("collection").getJSONArray("customIndexMapping");
    for (int i = 0; i < mapping.length(); i++) mapping.getJSONObject(i).put("dataType", "string");
    status(getNakshaClient().put("hub/handlers/" + id, saved.toString(), stream), 400);
  }

  @Test void removingAllMappingsMustNotBypassHandlerVerification() throws Exception {
    String id = "mapping_save_removed_mapping_handler";
    JSONObject h = handler(id, "mapping_save_removed_mapping_collection");
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    status(getNakshaClient().post("hub/spaces", space(id + "_space", id).toString(), stream));
    JSONObject saved = new JSONObject(getNakshaClient().get("hub/handlers/" + id, stream).body());
    saved.getJSONObject("properties").getJSONObject("collection").remove("customIndexMapping");
    status(getNakshaClient().put("hub/handlers/" + id, saved.toString(), stream), 409);
  }

  private void saveDirect(NakshaApp app, boolean upsert, JSONObject... json) {
    NakshaContext.newInstance("custom-index-direct-save", "test", null, true).attachToCurrentThread();
    WriteRequest request = new WriteRequest();
    for (JSONObject object : json) {
      EventHandlerConfig config = Objects.requireNonNull(Platform.javaProxy(Platform.fromJSON(object.toString()), EventHandlerConfig.class));
      request.add(upsert ? new Write().upsertFeature(app.getHub().getAdminMapId(), EVENT_HANDLERS, config)
          : new Write().createFeature(app.getHub().getAdminMapId(), EVENT_HANDLERS, config));
    }
    Response result = app.getHub().getSpaceStorage().useWriteSession(new SessionOptions(), writer -> writer.execute(request));
    assertTrue(result instanceof SuccessResponse, result.toString());
  }

  @Test void upsertMustProvisionMappedHandler(@NakshaAppInjection NakshaApp app) throws JSONException {
    JSONObject h = handler("mapping_save_upsert_handler", "mapping_save_upsert_collection");
    saveDirect(app, true, h);
    assertFalse(storedCollection(app, h.getJSONObject("properties").getString("storageId"), "mapping_save_upsert_collection").isEmpty(),
        "A mapped Handler saved with UPSERT must provision its collection");
  }

  @Test void batchCreateMustProvisionMappedHandlers(@NakshaAppInjection NakshaApp app) throws JSONException {
    JSONObject h1 = handler("mapping_save_batch_handler_one", "mapping_save_batch_collection_one");
    JSONObject h2 = handler("mapping_save_batch_handler_two", "mapping_save_batch_collection_two");
    saveDirect(app, false, h1, h2);
    assertFalse(storedCollection(app, h1.getJSONObject("properties").getString("storageId"), "mapping_save_batch_collection_one").isEmpty(),
        "A mapped Handler saved in a batch must provision its collection");
  }

  @Test void rejectedReadOnlySpaceSaveMustNotCreateCollection(@NakshaAppInjection NakshaApp app) throws Exception {
    String id = "mapping_save_forbidden_space_handler";
    JSONObject h = handler(id, "unused", false);
    h.getJSONObject("properties").remove("collection");
    String storageId = h.getJSONObject("properties").getString("storageId");
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    String collection = "mapping_save_forbidden_space_table";
    JSONObject s = mappedSpace("mapping_save_forbidden_space", id, collection);
    status(getNakshaClient().post("hub/spaces", s.toString(), stream, "Bearer " + TestUtil.readOnlyJwt()), 403);
    assertTrue(storedCollection(app, storageId, collection).isEmpty(), "403 must not create a physical collection");
  }

  @SafeVarargs
  private final JSONObject multiCollectionHandler(String id, Map.Entry<String, String>... collections) throws JSONException {
    JSONObject h = handler(id, "unused");
    JSONObject properties = h.getJSONObject("properties");
    properties.remove("collection");
    JSONArray definitions = new JSONArray();
    for (Map.Entry<String, String> collection : collections) {
      definitions.put(new JSONObject().put("id", collection.getKey()).put("customIndexMapping", new JSONArray().put(
          new JSONObject().put("jsonPath", new JSONArray().put("properties").put("score")).put("dataType", collection.getValue()))));
    }
    properties.put("collections", definitions);
    return h.put("className", MultiCollectionStorageHandler.class.getName());
  }

  @Test void subclassCollectionsAreCreatedOnHandlerSave(@NakshaAppInjection NakshaApp app) throws Exception {
    JSONObject h = multiCollectionHandler("mapping_save_multi_handler",
        Map.entry("mapping_save_multi_one", "int32"), Map.entry("mapping_save_multi_two", "string"));
    String storageId = h.getJSONObject("properties").getString("storageId");
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    assertFalse(storedCollection(app, storageId, "mapping_save_multi_one").isEmpty());
    assertFalse(storedCollection(app, storageId, "mapping_save_multi_two").isEmpty());
  }

  @Test void subclassWithConflictingCollectionDefinitionsIsRejected() throws Exception {
    JSONObject h = multiCollectionHandler("mapping_save_multi_conflict_handler",
        Map.entry("mapping_save_multi_conflict", "int32"), Map.entry("mapping_save_multi_conflict", "string"));
    status(getNakshaClient().post("hub/handlers", h.toString(), stream), 409);
    status(getNakshaClient().get("hub/handlers/mapping_save_multi_conflict_handler", stream), 404);
  }

  @Test void mappedHandlerWithoutAutoCreateIsRejected() throws Exception {
    JSONObject h = handler("mapping_save_no_autocreate_handler", "mapping_save_no_autocreate_collection");
    h.getJSONObject("properties").put("autoCreateCollection", false);
    status(getNakshaClient().post("hub/handlers", h.toString(), stream), 400);
    status(getNakshaClient().get("hub/handlers/mapping_save_no_autocreate_handler", stream), 404);
  }

  @Test void mappedSpaceOnHandlerWithoutAutoCreateIsRejected(@NakshaAppInjection NakshaApp app) throws Exception {
    String handlerId = "mapping_save_space_no_autocreate_handler";
    JSONObject h = handler(handlerId, "unused");
    h.getJSONObject("properties").remove("collection");
    h.getJSONObject("properties").put("autoCreateCollection", false);
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    String collection = "mapping_save_space_no_autocreate_table";
    JSONObject s = mappedSpace("mapping_save_space_no_autocreate", handlerId, collection);
    status(getNakshaClient().post("hub/spaces", s.toString(), stream), 400);
    status(getNakshaClient().get("hub/spaces/mapping_save_space_no_autocreate", stream), 404);
    assertTrue(storedCollection(app, h.getJSONObject("properties").getString("storageId"), collection).isEmpty());
  }

  @Test void disablingAutoCreateUnderMappedSpacesIsRejected() throws Exception {
    String handlerId = "mapping_save_mapped_spaces_handler";
    JSONObject h = handler(handlerId, "unused");
    h.getJSONObject("properties").remove("collection");
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    JSONObject s = mappedSpace("mapping_save_mapped_spaces_space", handlerId, "mapping_save_mapped_spaces_table");
    status(getNakshaClient().post("hub/spaces", s.toString(), stream));
    JSONObject saved = new JSONObject(getNakshaClient().get("hub/handlers/" + handlerId, stream).body());
    saved.getJSONObject("properties").put("autoCreateCollection", false);
    status(getNakshaClient().put("hub/handlers/" + handlerId, saved.toString(), stream), 400);
  }

  private JSONObject mappedSpace(String id, String handlerId, String collection) throws JSONException {
    JSONObject s = new JSONObject(TestUtil.loadFileOrFail("CustomIndexMapping/TC01_spaceMappingLifecycle/create_space.json"))
        .put("id", id).put("eventHandlerIds", new JSONArray().put(handlerId));
    s.getJSONObject("properties").getJSONObject("collection").put("id", collection);
    return s;
  }

  @Test void mappedSpaceOnSubclassWithoutAutoCreateIsRejected(@NakshaAppInjection NakshaApp app) throws Exception {
    JSONObject h = multiCollectionHandler("mapping_save_subclass_no_autocreate_handler");
    h.getJSONObject("properties").put("autoCreateCollection", false);
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    String collection = "mapping_save_subclass_no_autocreate_table";
    JSONObject s = mappedSpace("mapping_save_subclass_no_autocreate_space", h.getString("id"), collection);
    status(getNakshaClient().post("hub/spaces", s.toString(), stream), 400);
    status(getNakshaClient().get("hub/spaces/mapping_save_subclass_no_autocreate_space", stream), 404);
    assertTrue(storedCollection(app, h.getJSONObject("properties").getString("storageId"), collection).isEmpty());
  }

  @Test void disablingAutoCreateOnSubclassUnderMappedSpacesIsRejected() throws Exception {
    JSONObject h = multiCollectionHandler("mapping_save_subclass_mapped_spaces_handler");
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    JSONObject s = mappedSpace("mapping_save_subclass_mapped_spaces_space", h.getString("id"), "mapping_save_subclass_mapped_spaces_table");
    status(getNakshaClient().post("hub/spaces", s.toString(), stream));
    JSONObject saved = new JSONObject(getNakshaClient().get("hub/handlers/" + h.getString("id"), stream).body());
    saved.getJSONObject("properties").put("autoCreateCollection", false);
    status(getNakshaClient().put("hub/handlers/" + h.getString("id"), saved.toString(), stream), 400);
  }

  @Test void mappedSpaceOnSubclassWithHandlerCollectionIsRejected() throws Exception {
    JSONObject h = multiCollectionHandler("mapping_save_subclass_fixed_handler");
    h.getJSONObject("properties").put("collection", new JSONObject().put("id", "mapping_save_subclass_fixed_table"));
    status(getNakshaClient().post("hub/handlers", h.toString(), stream));
    JSONObject s = mappedSpace("mapping_save_subclass_fixed_space", h.getString("id"), "mapping_save_subclass_fixed_space_table");
    status(getNakshaClient().post("hub/spaces", s.toString(), stream), 409);
    status(getNakshaClient().get("hub/spaces/mapping_save_subclass_fixed_space", stream), 404);
  }
}
