package com.here.naksha.app.service.http.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Test;

class MaskingUtilTest {

  @Test
  void shouldMaskProperties() {
    // Given
    NakshaFeature feature = featureWithProps(mutableMapOf(
        "sensitiveObject", mutableMapOf(
            "some_entry_1", 123,
            "some_entry_2", "lorem ipsum"
        ),
        "headers", mutableMapOf(
            "Authorization", "secret stuff, do not look",
            "Content-Type", "application/json"
        ),
        "very", mutableMapOf(
            "nested", mutableMapOf(
                "map", mutableMapOf(
                    "to", mutableMapOf(
                        "sensitiveObject", mutableMapOf(
                            "foo", "bar"
                        )
                    )
                )
            )
        )
    ));

    // And:
    Set<String> lowercasedSensitiveProperties = Set.of("sensitiveobject", "authorization");

    // When:
    MaskingUtil.maskProperties(feature, lowercasedSensitiveProperties);

    // Then:
    assertPropertiesMatch(feature.getProperties(), Map.of(
        "sensitiveObject", MaskingUtil.MASK,
        "headers", Map.of(
            "Authorization", MaskingUtil.MASK,
            "Content-Type", "application/json"
        ),
        "very", Map.of(
            "nested", Map.of(
                "map", Map.of(
                    "to", Map.of(
                        "sensitiveObject", MaskingUtil.MASK
                    )
                )
            )
        )
    ));
  }


  private static void assertPropertiesMatch(Map actual, Map<String, Object> expected) {
    assertEquals(actual.size(), expected.size(), "Size mismatch when comparing properties");
    expected.forEach((key, expectedValue) -> {
      if (!actual.containsKey(key)) {
        fail("Expected property '%s' not found in the properties".formatted(key));
      }
      Object actualValue = actual.get(key);
      if (expectedValue instanceof Map expectedValueMap) {
        assertInstanceOf(Map.class, actualValue, "Property '%s' should be a map".formatted(key));
        assertPropertiesMatch((Map) actualValue, expectedValueMap);
      } else {
        assertEquals(expectedValue, actualValue,
            "Mismatch for property '%s' - expected: '%s', got: '%s'".formatted(key, expectedValue, actualValue));
      }
    });
  }

  private static NakshaFeature featureWithProps(Map<String, Object> props) {
    NakshaFeature xyzFeature = new NakshaFeature();
    xyzFeature.getProperties().putAll(props);
    return xyzFeature;
  }

  // We use this instead of simple `Map::of` because `MaskingUtil` relies on properties' `entrySet`
  // `Map::of` return immutable map, which entries do not support `Entry::setValue` method
  private static Map<String, Object> mutableMapOf(Object... args) {
    HashMap<String, Object> map = new HashMap<>();
    for (int i = 0; i < args.length; i += 2) {
      map.put(args[i].toString(), args[i + 1]);
    }
    return map;
  }
}