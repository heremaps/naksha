package com.here.naksha.app.service;

import static org.junit.jupiter.api.Assertions.*;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaAppInjection;
import com.here.naksha.app.common.TestUtil;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import naksha.base.NakshaError;
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

/** Space and Handler saves are verified against an existing collection; a missing collection is created later. */
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

  private void writeFeatures(String spaceId) throws Exception {
    String features = TestUtil.loadFileOrFail("CustomIndexMapping/setup/create_features.json");
    status(getNakshaClient().post("hub/spaces/" + spaceId + "/features", features, stream));
  }

  private JSONObject withType(JSONObject handler, String type) throws JSONException {
    handler.getJSONObject("properties").getJSONObject("collection").getJSONArray("customIndexMapping")
        .getJSONObject(0).put("dataType", type);
    return handler;
  }

  @Test void handlerConflictingWithExistingCollectionIsRejected(@NakshaAppInjection NakshaApp app) throws Exception {
    JSONObject first = handler("mapping_save_first_handler", "mapping_save_shared");
    status(getNakshaClient().post("hub/handlers", first.toString(), stream));
    status(getNakshaClient().post("hub/spaces", space("mapping_save_first_space", "mapping_save_first_handler").toString(), stream));
    writeFeatures("mapping_save_first_space");
    String storageId = first.getJSONObject("properties").getString("storageId");
    assertFalse(storedCollection(app, storageId, "mapping_save_shared").isEmpty());

    JSONObject conflicting = withType(handler("mapping_save_conflicting_handler", "mapping_save_shared"), "string");
    status(getNakshaClient().post("hub/handlers", conflicting.toString(), stream), 409);
    status(getNakshaClient().get("hub/handlers/mapping_save_conflicting_handler", stream), 404);

    JSONObject matching = handler("mapping_save_matching_handler", "mapping_save_shared");
    status(getNakshaClient().post("hub/handlers", matching.toString(), stream));
  }

  @Test void mappingsAreSavedWhileTheCollectionDoesNotExist(@NakshaAppInjection NakshaApp app) throws Exception {
    JSONObject first = handler("mapping_save_absent_first_handler", "mapping_save_absent");
    JSONObject second = withType(handler("mapping_save_absent_second_handler", "mapping_save_absent"), "string");
    status(getNakshaClient().post("hub/handlers", first.toString(), stream));
    status(getNakshaClient().post("hub/handlers", second.toString(), stream));
    String storageId = first.getJSONObject("properties").getString("storageId");
    assertTrue(storedCollection(app, storageId, "mapping_save_absent").isEmpty());
  }
}
