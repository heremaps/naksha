/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.core.util.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.lib.core.view.ViewDeserialize.User;
import com.here.naksha.lib.core.view.ViewSerialize;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class JsonObjectTest {

  @Test
  void raw_basics() {
    final JsonObject map = new JsonObject();
    assertEquals(0, map.size());

    map.put("foo", 10);
    assertEquals(1, map.size());
    Object foo = map.get("foo");
    Integer fooInt = assertInstanceOf(Integer.class, foo);
    assertEquals(10, fooInt);

    foo = map.remove("foo");
    fooInt = assertInstanceOf(Integer.class, foo);
    assertEquals(10, fooInt);
    assertEquals(0, map.size());
  }

  @Test
  void raw_iterate() {
    final JsonObject map = new JsonObject();
    assertEquals(0, map.size());
    // We expect that no memory used.

    map.put("foo", "Hello");
    map.put("bar", "World");
    map.put("end", 5);
    assertEquals(3, map.size());
    // We expect that a single table is enough, 16 entries each with 8/16 byte (two words/pointer),
    // so 64 to 128 byte.
    assertEquals("Hello", map.get("foo"));
    assertEquals("World", map.get("bar"));
    assertEquals(5, map.get("end"));

    // Note: We know that all data is in a single array in a “random” order and both iterators must
    // iterate in the same order!
    //       As seen, we add keys in the order "foo", "bar", "end" and read them in the order "bar",
    // "foo", "end", so the order
    //       is based upon the hash-code, not how they are added.
    final Set<@NotNull String> keySet = map.keySet();
    final Iterator<@NotNull String> keyIt = keySet.iterator();
    final Collection<@Nullable Object> values = map.values();
    final Iterator<@NotNull Object> valueIt = values.iterator();

    String key;
    Object value;

    assertTrue(keyIt.hasNext());
    assertTrue(valueIt.hasNext());
    key = keyIt.next();
    value = valueIt.next();
    assertNotNull(key);
    assertNotNull(value);
    assertEquals("bar", key);
    assertEquals("World", value);

    assertTrue(keyIt.hasNext());
    assertTrue(valueIt.hasNext());
    key = keyIt.next();
    value = valueIt.next();
    assertNotNull(key);
    assertNotNull(value);
    assertEquals("foo", key);
    assertEquals("Hello", value);

    assertTrue(keyIt.hasNext());
    assertTrue(valueIt.hasNext());
    key = keyIt.next();
    value = valueIt.next();
    assertNotNull(key);
    assertNotNull(value);
    assertEquals("end", key);
    assertEquals(5, value);

    assertFalse(keyIt.hasNext());
    assertFalse(valueIt.hasNext());
  }

  private static class TestObject extends JsonObject {

    private TestObject() {
      foo = "test";
      foo2 = "xyz";
    }

    public String foo;

    @JsonProperty("bar")
    private String foo2;
  }

  @Test
  void test_extended_basics() {
    final TestObject map = new TestObject();
    assertEquals(2, map.size());
    Object foo = map.get("foo");
    assertEquals("test", foo);
    assertEquals("test", map.foo);
    Object bar = map.get("bar");
    assertEquals("xyz", bar);
    assertEquals("xyz", map.foo2);

    map.remove("foo");
    assertEquals(1, map.size());
    map.put("foo", "new");
    assertEquals(2, map.size());
    assertEquals("new", map.foo);

    map.clear();
    //noinspection ConstantConditions
    assertEquals(0, map.size());
  }

  @Test
  void test_extended_iterator() {
    Entry<@NotNull String, @Nullable Object> entry;

    final TestObject map = new TestObject();
    map.put("newKey", "newValue");
    Iterator<Entry<@NotNull String, @Nullable Object>> mapIt = map.iterator();

    assertTrue(mapIt.hasNext());
    entry = mapIt.next();
    assertNotNull(entry);
    assertEquals("foo", entry.getKey());
    assertEquals("test", entry.getValue());

    entry = mapIt.next();
    assertEquals("bar", entry.getKey());
    assertEquals("xyz", entry.getValue());

    entry = mapIt.next();
    assertEquals("newKey", entry.getKey());
    assertEquals("newValue", entry.getValue());

    assertFalse(mapIt.hasNext());
  }

  private static final String SERIALIZED = "{\"bar\":\"xyz\",\"foo\":\"test\",\"newKey\":\"newValue\"}";

  @Test
  void test_serialization() throws JsonProcessingException {
    final TestObject map = new TestObject();
    map.put("newKey", "newValue");
    try (var json = Json.get()) {
      final String s = json.writer(ViewSerialize.User.class, false).writeValueAsString(map);
      assertEquals(SERIALIZED, s);
    }
  }

  @Test
  void test_deserialization() throws IOException {
    try (var json = Json.get()) {
      final TestObject map = json.reader(User.class).readValue(SERIALIZED, TestObject.class);
      assertEquals(3, map.size());
      Object foo = map.get("foo");
      assertEquals("test", foo);
      assertEquals("test", map.foo);
      Object bar = map.get("bar");
      assertEquals("xyz", bar);
      assertEquals("xyz", map.foo2);
      Object newValue = map.get("newKey");
      assertEquals(newValue, "newValue");

      // Test String interning, all strings need to be the same instance!
      final TestObject map2 = json.reader(User.class).readValue(SERIALIZED, TestObject.class);
      assertSame(map.get("foo"), map2.get("foo"));
      assertSame(map.get("bar"), map2.get("bar"));
      assertSame(map.get("newKey"), map2.get("newKey"));
    }
  }
}
