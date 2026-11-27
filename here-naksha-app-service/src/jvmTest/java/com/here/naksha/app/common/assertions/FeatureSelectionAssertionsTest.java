package com.here.naksha.app.common.assertions;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class FeatureSelectionAssertionsTest {
  private static final String EXPECTED = """
      {"type":"FeatureCollection","features":[
        {"id":"a","properties":{"speed":30},"geometry":{"type":"Point","coordinates":[8,50,0]}},
        {"id":"b","properties":{"speed":40},"geometry":{"type":"Point","coordinates":[9,51,0]}}
      ]}
      """;

  @Test
  void acceptsReorderedFeaturesButNotReorderedCoordinates() {
    String reordered = """
        {"type":"FeatureCollection","features":[
          {"id":"b","properties":{"speed":40},"geometry":{"type":"Point","coordinates":[9,51,0]}},
          {"id":"a","properties":{"speed":30},"geometry":{"type":"Point","coordinates":[8,50,0]}}
        ]}
        """;
    compare(reordered);
    assertThrows(AssertionError.class, () -> compare(reordered.replace("[8,50,0]", "[50,8,0]")));
  }

  @Test
  void rejectsUnselectedPropertiesAndMissingRequestedProperties() {
    assertThrows(AssertionError.class, () -> compare(EXPECTED.replace("\"speed\":30", "\"speed\":30,\"unexpected\":true")));
    assertThrows(AssertionError.class, () -> compare(EXPECTED.replace("\"speed\":30", "")));
  }

  @Test
  void rejectsDuplicateFeatureIds() {
    assertThrows(AssertionError.class, () -> compare(EXPECTED.replace("\"id\":\"b\"", "\"id\":\"a\"")));
  }

  @SuppressWarnings("unchecked")
  private static void compare(String body) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.body()).thenReturn(body);
    ResponseAssertions.assertThat(response).hasJsonBodyWithUnorderedFeatures(EXPECTED, "selection mismatch");
  }
}
