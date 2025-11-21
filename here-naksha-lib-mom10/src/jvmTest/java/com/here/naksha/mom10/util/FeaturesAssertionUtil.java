package com.here.naksha.mom10.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.mom10.MetaProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;

public class FeaturesAssertionUtil {

  private FeaturesAssertionUtil() {
  }

  private static final String MODERATION_INFO_PATH =
      NakshaFeature.PROPERTIES_KEY + "." + MetaProperties.META + "." + MetaProperties.MODERATION_INFO;

  private static final Set<String> MODERATION_INFO_NULLABLE_FIELDS_SET_BY_NAKSHA = Set.of(
      "parentLink", "originId"
  );

  private static final Set<String> IGNORE_IF_NULL = Set.of(
      NakshaProperties.DELTA_KEY,
      NakshaProperties.META_KEY,
      NakshaProperties.XYZ_ACTIVITY_LOG_NS
  );

  public static void assertFeaturesEqual(NakshaFeature expectedFeature, NakshaFeature actualFeature) {
    assertMapsEqual(expectedFeature, actualFeature, "");
  }

  private static void assertMapsEqual(Map expected, Map actual, String path) {
    if (Objects.equals(path, MODERATION_INFO_PATH)) {
      verifyAndDropNullsSetByNaksha(expected, actual);
    }
    if (Objects.equals(path, NakshaFeature.PROPERTIES_KEY)) {
      dropIgnoredIfNulls(expected, actual);
    }
    assertEquals(expected.size(), actual.size(), "Map size mismatch under path: " + path);
    expected.forEach((key, expectedValue) -> {
      Object actualValue = actual.get(key);
      assertObjectEqual(expectedValue, actualValue, (path.isEmpty() ? key.toString() : path + "." + key));
    });
  }

  private static void dropIgnoredIfNulls(Map expected, Map actual) {
    for (String key : IGNORE_IF_NULL) {
      if (expected.containsKey(key) && expected.get(key) == null) {
        expected.remove(key);
      }
      if (actual.containsKey(key) && actual.get(key) == null) {
        actual.remove(key);
      }
    }
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
