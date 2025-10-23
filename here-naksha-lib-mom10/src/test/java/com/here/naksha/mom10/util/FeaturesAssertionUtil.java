package com.here.naksha.mom10.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.util.json.JsonEnum;
import com.here.naksha.mom10.MetaProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FeaturesAssertionUtil {

  private FeaturesAssertionUtil() {
  }

  private static final String MODERATION_INFO_PATH =
      XyzFeature.PROPERTIES + "." + MetaProperties.META + "." + MetaProperties.MODERATION_INFO;

  private static final Set<String> MODERATION_INFO_NULLABLE_FIELDS_SET_BY_NAKSHA = Set.of(
      "parentLink", "originId"
  );

  public static void assertFeaturesEqual(XyzFeature expectedFeature, XyzFeature actualFeature) {
    assertMapsEqual(expectedFeature, actualFeature, "");
  }

  private static void assertMapsEqual(Map expected, Map actual, String path) {
    if (Objects.equals(path, MODERATION_INFO_PATH)) {
      verifyAndDropNullsSetByNaksha(expected, actual);
    }
    assertEquals(expected.size(), actual.size(), "Map size mismatch under path: " + path);
    expected.forEach((key, expectedValue) -> {
      Object actualValue = actual.get(key);
      assertObjectEqual(expectedValue, actualValue, (path.isEmpty() ? key.toString() : path + "." + key));
    });
  }

  private static void verifyAndDropNullsSetByNaksha(Map expectedModerationInfo, Map actualModerationInfo) {
    Set<String> additionalKeys = additionalKeys(expectedModerationInfo.keySet(), actualModerationInfo.keySet());
    assertTrue(MODERATION_INFO_NULLABLE_FIELDS_SET_BY_NAKSHA.containsAll(additionalKeys));
    additionalKeys.forEach(key -> {
      assertNull(actualModerationInfo.get(key));
      actualModerationInfo.remove(key);
    });
  }

  private static void assertObjectEqual(Object expectedValue, Object actualValue, String path) {
    if (expectedValue == null) {
      assertNull(actualValue, "Expected null value under path: " + path);
    } else if (expectedValue instanceof Map expectedMapValue) {
      assertInstanceOf(Map.class, actualValue);
      assertMapsEqual(expectedMapValue, (Map) actualValue, path);
    } else if (expectedValue instanceof List expectedListValue) {
      assertInstanceOf(List.class, actualValue);
      assertListsEqual(expectedListValue, (List) actualValue, path);
    } else if (expectedValue instanceof JsonEnum expectedJsonEnum && actualValue instanceof String actualString) {
      assertEquals(expectedJsonEnum.value(), actualString,
          "Expected " + expectedValue + " but got " + actualValue + " under path: " + path);
    } else if (actualValue instanceof JsonEnum actualJsonEnum && expectedValue instanceof String expectedString) {
      assertEquals(expectedString, actualJsonEnum.value(),
          "Expected " + expectedValue + " but got " + actualValue + " under path: " + path);
    } else {
      assertEquals(expectedValue, actualValue, "Expected " + expectedValue + " but got " + actualValue + " under path: " + path);
    }
  }

  private static void assertListsEqual(List expected, List actual, String path) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      assertObjectEqual(expected.get(i), actual.get(i), path + "[" + i + "]");
    }
  }

  private static Set<String> additionalKeys(Set<String> expected, Set<String> actual) {
    // moving keys to simple hash set as Naksha's MapKeySet s buggy
    Set<String> actualKeys = new HashSet<>(actual);
    Set<String> expectedKeys = new HashSet<>(expected);
    actualKeys.removeAll(expectedKeys);
    return actualKeys;
  }
}
