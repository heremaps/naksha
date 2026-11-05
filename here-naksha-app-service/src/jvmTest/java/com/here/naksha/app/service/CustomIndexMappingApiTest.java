package com.here.naksha.app.service;

import static com.here.naksha.app.common.FeatureMetadata.ExtractionUtil.featureMetadataFromFeatureResp;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.urlEncoded;
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

    // 2. The first feature write creates the collection with its custom indices.
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

    // 4. A mapped inequality is translated as a negated equality.
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=ne=10", streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);

    // 4b. Comma separated values combined with a tag-list containment are translated as a whole.
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=10,51&p.labels=cs=red", streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);

    // 4c. URL encoded JSON arrays require all of their tags, comma separated arrays are OR, repeated parameters AND.
    for (String predicate : new String[] {
        "p.labels=cs=" + urlEncoded("[\"red\"]"),
        // The REST decoder does not decode '+' into a space.
        "p.labels=cs=" + urlEncoded(" [\"red\"] ").replace("+", "%20"),
        "p.labels=cs=" + urlEncoded("[\"red\",\"blue\"]") + "," + urlEncoded("[\"red\"]"),
        "p.labels=cs=" + urlEncoded("[\"red\"]") + "&p.labels=cs=" + urlEncoded("[]"),
        "p.score=51&p.labels=cs=" + urlEncoded("[]")}) {
      assertThat(getNakshaClient().get(spaceUrl + "/search?" + predicate, streamId))
          .hasStatus(200)
          .hasStreamIdHeader(streamId)
          .hasJsonBodyFromFile(SEARCH_RESPONSE);
    }

    // 4d. Numeric operands reach the storage unchanged, it rejects the decimal operand for the INT64 member.
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=gt=50.5", streamId))
        .hasStatus(400)
        .hasStreamIdHeader(streamId);

    // 5. Rename the Space using its current version; the mappings are unchanged, so the update is accepted.
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

    // 6. Reject a changed mapping, because a Space update must keep the saved mappings.
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
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=gt=50&limit=1", streamId))
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

    // 2. The first feature write creates the collection defined by the Handler.
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
    assertThat(getNakshaClient().get(spaceUrl + "/search?p.score=gt=50&limit=1", streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBodyFromFile(SEARCH_RESPONSE);
  }

  @Test
  @DisplayName("Shared collection: Space creation does not compare the existing physical schema")
  void sharedCollectionAllowsConflictingSpaceConfiguration() throws Exception {
    final String streamId = UUID.randomUUID().toString();
    final String firstSpace = "hub/spaces/shared_mapping_space_a";
    final String secondSpace = "hub/spaces/shared_mapping_space_b";
    assertThat(getNakshaClient().post("hub/handlers", loadFileOrFail(SHARED_CASE + "create_handler.json"), streamId))
        .hasStatus(200);
    assertThat(getNakshaClient().post("hub/spaces", loadFileOrFail(SHARED_CASE + "create_space_a.json"), streamId))
        .hasStatus(200);

    // The first feature write creates the collection from the first Space's mapping.
    final String features = loadFileOrFail(FEATURES);
    assertThat(getNakshaClient().post(firstSpace + "/features", features, streamId))
        .hasStatus(200)
        .hasInsertedCountMatchingWithFeaturesInRequest(features);

    // Creating a configuration does not check the existing physical collection.
    assertThat(getNakshaClient().post("hub/spaces", loadFileOrFail(SHARED_CASE + "create_conflicting_space_b.json"), streamId))
        .hasStatus(200)
        .hasStreamIdHeader(streamId);
    assertThat(getNakshaClient().get(secondSpace, streamId)).hasStatus(200);

    final String compatibleSpace = loadFileOrFail(SHARED_CASE + "create_compatible_space_b.json")
        .replace("shared_mapping_space_b", "shared_mapping_space_c");
    assertThat(getNakshaClient().post("hub/spaces", compatibleSpace, streamId))
        .hasStatus(200);
    assertNoGeneratedSchemaInConfiguration(getNakshaClient().get(firstSpace, streamId));
    assertNoGeneratedSchemaInConfiguration(getNakshaClient().get(secondSpace, streamId));
    assertThat(getNakshaClient().get("hub/spaces/shared_mapping_space_c/search?p.score=51&limit=1", streamId))
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
