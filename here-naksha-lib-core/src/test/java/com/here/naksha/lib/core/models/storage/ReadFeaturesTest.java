package com.here.naksha.lib.core.models.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.lib.core.util.json.Json;
import naksha.model.request.ReadFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadFeaturesTest {

  @Test
  void testShallowCopy() throws JsonProcessingException {
    // given
    ReadFeatures readFeatures = new ReadFeatures();
    Json jsonGenerator = Json.get();

    // when
    String json = jsonGenerator.writer().writeValueAsString(readFeatures);

    // then
    String expectedJson = "{\"collectionIds\":[],\"limit\":100000,\"limitVersions\":1,\"noFeature\":false,\"noGeoRef\":false,\"noGeometry\":false,\"noMeta\":false,\"noTags\":false,\"queryDeleted\":false,\"queryHistory\":false,\"resultFilter\":[],\"returnHandle\":false}";
    assertEquals(expectedJson, json, "there is a property change in ReadFeatures, add it to shallowCopy and update json");
  }
}
