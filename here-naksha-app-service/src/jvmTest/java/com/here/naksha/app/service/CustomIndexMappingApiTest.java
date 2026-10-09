package com.here.naksha.app.service;

import static com.here.naksha.app.common.FeatureMetadata.ExtractionUtil.featureMetadataFromFeatureResp;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.here.naksha.app.common.ApiTest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomIndexMappingApiTest extends ApiTest {

  private static final String SPACE_CASE = "CustomIndexMapping/TC01_spaceMappingLifecycle/";
  private static final String HANDLER_CASE = "CustomIndexMapping/TC02_handlerMappingLifecycle/";
  private static final String SHARED_CASE = "CustomIndexMapping/TC03_sharedCollectionDefinitions/";
  private static final String BOOLEAN_CASE = "CustomIndexMapping/TC04_booleanMapping/";
  private static final String HIDDEN_CASE = "CustomIndexMapping/TC05_hiddenSpaceMapping/";
  private static final String FEATURES = "CustomIndexMapping/setup/create_features.json";
  private static final String SEARCH_RESPONSE = "CustomIndexMapping/search_response.json";

  @Test
  @DisplayName("Space mapping: create, ingest, search, rename, reject schema change and search again")
  void customSpaceMappingWorksThroughExistingRestSearchAndRejectsMutation() throws Exception {
    final String streamId = UUID.randomUUID().toString();
    final String spaceUrl = "hub/spaces/custom_mapping_api_space";

    // 1. Register the storage Handler, then a Space declaring score and labels mappings.
    final String handlerJson = loadFileOrFail(SPACE_CASE + "create_event_handler.json");
    assertThat(getNakshaClient().post("hub/handlers", handlerJson, streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId);
    final String spaceJson = loadFileOrFail(SPACE_CASE + "create_space.json");
    assertThat(getNakshaClient().post("hub/spaces", spaceJson, streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId);

    // 2. The collection and its custom indices were created with the Space, features can be written.
    final String featuresJson = loadFileOrFail(FEATURES);
    assertThat(getNakshaClient().post(spaceUrl + "/features", featuresJson, streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasInsertedCountMatchingWithFeaturesInRequest(featuresJson);

    // 3. Search through a supported mapped predicate with a one-feature result limit.
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=51&limit=1", streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);

    // 4. An unsupported predicate keeps the existing property-filtering behavior.
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=ne=10", streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);

    // 5. Rename the Space using its current version; the unchanged collection is verified before the Space is saved.
    final HttpResponse<String> current = getNakshaClient().get(spaceUrl, streamId);
    assertThat(current).hasStatus(200).hasStreamIdHeader(streamId);
    final String updateJson = loadUpdateWithCurrentVersion(SPACE_CASE + "update_space.json", current);
    assertThat(getNakshaClient().put(spaceUrl, updateJson, streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId);
    final HttpResponse<String> saved = getNakshaClient().get(spaceUrl, streamId);
    assertThat(saved)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SPACE_CASE + "updated_space_response.json");
    assertNoGeneratedSchemaInConfiguration(saved);

    // 6. Reject a changed mapping, because the schema of an existing collection must not change.
    final String changedMapping = loadUpdateWithCurrentVersion(SPACE_CASE + "change_mapping.json", saved);
    assertThat(getNakshaClient().put(spaceUrl, changedMapping, streamId))
        .hasStatus(409)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SPACE_CASE + "mapping_conflict_response.json");
    final HttpResponse<String> afterRejectedUpdate = getNakshaClient().get(spaceUrl, streamId);
    assertThat(afterRejectedUpdate)
        .hasStatus(200)
        .hasJsonBodyFromFile(SPACE_CASE + "updated_space_response.json");
    assertNoGeneratedSchemaInConfiguration(afterRejectedUpdate);

    // 7. A subsequent request reloads the mapping and can still search the indexed property.
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=gt=50.5&limit=1", streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);
  }

  @Test
  @DisplayName("Handler mapping: create, ingest, search, rename, read configuration and search again")
  void handlerMappingSurvivesRealRestLazyCreationAndMetadataUpdate() throws Exception {
    final String streamId = UUID.randomUUID().toString();
    final String handlerUrl = "hub/handlers/mapped_handler_lifecycle";
    final String spaceUrl = "hub/spaces/mapped_handler_space";

    // 1. Declare the mapping on the Handler; its Space supplies no collection definition.
    final String handlerJson = loadFileOrFail(HANDLER_CASE + "create_event_handler.json");
    assertThat(getNakshaClient().post("hub/handlers", handlerJson, streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId);
    final String spaceJson = loadFileOrFail(HANDLER_CASE + "create_space.json");
    assertThat(getNakshaClient().post("hub/spaces", spaceJson, streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId);

    // 2. The Handler's collection was created with the Handler, features can be written.
    final String featuresJson = loadFileOrFail(FEATURES);
    assertThat(getNakshaClient().post(spaceUrl + "/features", featuresJson, streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasInsertedCountMatchingWithFeaturesInRequest(featuresJson);

    // 3. The Space's search resolves the member from the Handler mapping.
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=51&limit=1", streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);

    // 4. Rename the Handler using its current version, then reload its saved configuration.
    final HttpResponse<String> current = getNakshaClient().get(handlerUrl, streamId);
    assertThat(current).hasStatus(200).hasStreamIdHeader(streamId);
    final String updateJson = loadUpdateWithCurrentVersion(HANDLER_CASE + "update_event_handler.json", current);
    assertThat(getNakshaClient().put(handlerUrl, updateJson, streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId);
    final HttpResponse<String> saved = getNakshaClient().get(handlerUrl, streamId);
    assertThat(saved)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(HANDLER_CASE + "updated_handler_response.json");
    assertNoGeneratedSchemaInConfiguration(saved);

    // 5. A later search still uses the Handler mapping after the metadata update.
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=gt=50.5&limit=1", streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);
  }

  @Test
  @DisplayName("Shared collection: reject a conflicting Space before ingestion, accept a matching one and search")
  void sharedCollectionRejectsConflictingSpaceBeforeFirstFeatureWrite() throws Exception {
    final String streamId = UUID.randomUUID().toString();
    final String firstSpace = "hub/spaces/shared_mapping_space_a";
    final String secondSpace = "hub/spaces/shared_mapping_space_b";
    assertThat(getNakshaClient().post("hub/handlers", loadFileOrFail(SHARED_CASE + "create_handler.json"), streamId))
        .hasStatus(200);
    assertThat(getNakshaClient().post("hub/spaces", loadFileOrFail(SHARED_CASE + "create_space_a.json"), streamId))
        .hasStatus(200);

    // No features have been written, but the first Space created the collection, so the second one is verified against it.
    assertThat(getNakshaClient().post("hub/spaces", loadFileOrFail(SHARED_CASE + "create_conflicting_space_b.json"), streamId))
        .hasStatus(409)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SHARED_CASE + "conflict_response.json");
    assertThat(getNakshaClient().get(secondSpace, streamId)).hasStatus(404);

    assertThat(getNakshaClient().post("hub/spaces", loadFileOrFail(SHARED_CASE + "create_compatible_space_b.json"), streamId))
        .hasStatus(200);
    assertNoGeneratedSchemaInConfiguration(getNakshaClient().get(firstSpace, streamId));
    assertNoGeneratedSchemaInConfiguration(getNakshaClient().get(secondSpace, streamId));
    final String features = loadFileOrFail(FEATURES);
    assertThat(getNakshaClient().post(firstSpace + "/features", features, streamId))
        .hasStatus(200)
        .hasInsertedCountMatchingWithFeaturesInRequest(features);
    assertThat(getNakshaClient().get(secondSpace + "/search?p.score=51&limit=1", streamId))
        .hasStatus(200)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);
  }

  @Test
  @DisplayName("Boolean mapping: lazy creation and REST filtering for true, false, null and missing values")
  void booleanMappingWorksThroughRestSearchWithoutAnIndex() throws Exception {
    final String streamId = UUID.randomUUID().toString();
    final String spaceUrl = "hub/spaces/boolean_mapping_space";
    assertThat(getNakshaClient().post("hub/handlers", loadFileOrFail(BOOLEAN_CASE + "create_handler.json"), streamId))
        .hasStatus(200);
    assertThat(getNakshaClient().post("hub/spaces", loadFileOrFail(BOOLEAN_CASE + "create_space.json"), streamId))
        .hasStatus(200);
    final String features = loadFileOrFail(BOOLEAN_CASE + "create_features.json");
    assertThat(getNakshaClient().post(spaceUrl + "/features", features, streamId))
        .hasStatus(200)
        .hasInsertedCountMatchingWithFeaturesInRequest(features);

    for (String predicate : new String[] {"p.active=true", "p.active=ne=false", "p.active=true&p.score=10"}) {
      assertThat(getNakshaClient().get(spaceUrl + "/search?" + predicate, streamId))
          .hasStatus(200)
          .hasStreamIdHeader(streamId)
          .hasJsonBodyFromFile(BOOLEAN_CASE + "true_response.json");
    }
    for (String predicate : new String[] {"p.active=false", "p.active=ne=true"}) {
      assertThat(getNakshaClient().get(spaceUrl + "/search?" + predicate, streamId))
          .hasStatus(200)
          .hasStreamIdHeader(streamId)
          .hasJsonBodyFromFile(BOOLEAN_CASE + "false_response.json");
    }
    final HttpResponse<String> saved = getNakshaClient().get(spaceUrl, streamId);
    assertThat(saved).hasStatus(200);
    assertNoGeneratedSchemaInConfiguration(saved);
  }

  @Test
  @DisplayName("Hidden mapping: reject a Space mapping that its Handler collection would override")
  void spaceMappingIsRejectedWhenHandlerDefinesTheCollection() throws Exception {
    final String streamId = UUID.randomUUID().toString();
    assertThat(getNakshaClient().post("hub/handlers", loadFileOrFail(HIDDEN_CASE + "create_handler.json"), streamId))
        .hasStatus(200);
    assertThat(getNakshaClient().post("hub/spaces", loadFileOrFail(HIDDEN_CASE + "create_space.json"), streamId))
        .hasStatus(409)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(HIDDEN_CASE + "conflict_response.json");
    assertThat(getNakshaClient().get("hub/spaces/hidden_mapping_space", streamId)).hasStatus(404);
  }

  private static String loadUpdateWithCurrentVersion(String fixture, HttpResponse<String> current) {
    return loadFileOrFail(fixture).replace("${uuid}", featureMetadataFromFeatureResp(current.body()).uuid());
  }

  private static void assertNoGeneratedSchemaInConfiguration(HttpResponse<String> response) throws Exception {
    final JSONObject collection = new JSONObject(response.body())
        .getJSONObject("properties")
        .getJSONObject("collection");
    assertFalse(collection.has("members"), "Generated members must not be saved in configuration");
    assertFalse(collection.has("indices"), "Generated indices must not be saved in configuration");
  }
}
