package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Mom10Test extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
  private static final String SPACE_ID = "mom_10_test_space";

  @BeforeAll
  static void setup() {
    setupSpaceAndRelatedResources(nakshaClient, "Mom10/setup");
  }

  @Test
  void shouldCreateMom10Feature() throws URISyntaxException, IOException, InterruptedException {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Given: Create Features request with MOM 10 aligned payload
    final String featuresJson = loadFileOrFail("Mom10/testCreate/features.json");
    String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", featuresJson, streamId);

    // Then: Feature is correctly stored
    assertThat(response)
        .hasStatus(200)
        .hasJsonBody(featuresJson)
        .hasStreamIdHeader(streamId);
  }

  @Test
  void shouldReadCreatedMom10Feature() throws URISyntaxException, IOException, InterruptedException {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Given: Create Features request with MOM 10 aligned payload
    final String createFeatureJson = loadFileOrFail("Mom10/testRead/features.json");
    String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    HttpResponse<String> createResp = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);

    // And: We query for this feature
    HttpResponse<String> getResp = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?id=mom_rft_id", streamId);

    // Then: Stored feature is returned
    assertThat(getResp)
        .hasStatus(200)
        .hasJsonBody(createFeatureJson)
        .hasStreamIdHeader(streamId);
  }

  @Test
  void shouldPopulateOldNamespaces() throws URISyntaxException, IOException, InterruptedException {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Given: Create Features request with MOM 10 aligned payload
    final String createFeatureJson = loadFileOrFail("Mom10/testOldNamespaces/features.json");
    final String patchFeatureJson = loadFileOrFail("Mom10/testOldNamespaces/patch.json");
    final String expectedGetResp = loadFileOrFail("Mom10/testOldNamespaces/response.json");
    String featureId = "mom_10_patch_f";
    String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    HttpResponse<String> createResp = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);

    // And: We do little hacking - patching the feature with different `modelVersion` so that MOM 10 transformation don't happen
    HttpResponse<String> patchResp = getNakshaClient().patch("hub/spaces/" + SPACE_ID + "/features/" + featureId, patchFeatureJson, streamId);
    assertThat(patchResp).hasStatus(200);

    // And: We query for the patched feature that was transformed during the write phase (it was MOM 10) but will not during read phase (it was not MOM 10)
    HttpResponse<String> getResp = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?id=" + featureId, streamId);

    // Then: We get original feature with additional namespaces
    assertThat(getResp)
        .hasStatus(200)
        .hasJsonBody(expectedGetResp)
        .hasStreamIdHeader(streamId);
  }

  @Test
  void shouldDropOldNamespaces() throws URISyntaxException, IOException, InterruptedException {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Given: Create Features request with feature that is pre-MOM 10
    final String createFeatureJson = loadFileOrFail("Mom10/testDropNamespaces/features.json");
    final String patchFeatureJson = loadFileOrFail("Mom10/testDropNamespaces/patch.json");
    final String expectedGetResp = loadFileOrFail("Mom10/testDropNamespaces/response.json");
    String featureId = "test_drop_f";
    String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    HttpResponse<String> createResp = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);

    // And: We do little hacking - patching the feature with different `modelVersion` (MOM 10) so that MOM 10 transformation will happen
    HttpResponse<String> patchResp = getNakshaClient().patch("hub/spaces/" + SPACE_ID + "/features/" + featureId, patchFeatureJson, streamId);
    assertThat(patchResp).hasStatus(200);

    // And: We query for the patched feature that was not transformed during the write phase (it was pre MOM 10) but will during read phase (it was MOM 10)
    HttpResponse<String> getResp = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?id=" + featureId, streamId);

    // Then: We get original feature with dropped old namespaces
    assertThat(getResp)
        .hasStatus(200)
        .hasJsonBody(expectedGetResp)
        .hasStreamIdHeader(streamId);
  }
}
