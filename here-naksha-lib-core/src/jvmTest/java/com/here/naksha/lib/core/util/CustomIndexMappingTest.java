package com.here.naksha.lib.core.util;

import static org.junit.jupiter.api.Assertions.*;

import naksha.model.objects.CustomIndexMapping;
import naksha.model.objects.CustomIndexMappingList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.base.Platform;
import naksha.model.objects.*;
import org.junit.jupiter.api.Test;

class CustomIndexMappingTest {
  private CustomIndexMapping mapping(String key) {
    return mapping(key, MemberType.STRING);
  }

  private CustomIndexMapping mapping(String key, MemberType type) {
    return new CustomIndexMapping(new JsonPath("properties", key), type);
  }

  private NakshaCollection definition(CustomIndexMapping... mappings) {
    NakshaCollection definition = new NakshaCollection("mapped", "catalog");
    definition.setCustomIndexMapping(new CustomIndexMappingList(mappings));
    return definition;
  }

  private NakshaCollection nativeCollection(NakshaCollection definition) {
    return CollectionIndexPolicy.toNativeCollection(definition, "physical", "target");
  }

  private Member memberForPath(NakshaCollection compiled, JsonPath path) {
    return compiled.getMembers().stream()
        .filter(member -> member != null && CustomIndexMappingCompiler.samePath(member.getPath(), path))
        .findFirst().orElse(null);
  }

  private List<String> indexNames(NakshaCollection definition) {
    return definition.getIndices().stream().map(Index::getName).collect(Collectors.toList());
  }

  @Test void namesFollowSortedPaths() {
    NakshaCollection compiled = nativeCollection(definition(mapping("status"), mapping("rating", MemberType.INT32)));
    assertEquals("cm01", memberForPath(compiled, new JsonPath("properties", "rating")).getName());
    assertEquals("cm02", memberForPath(compiled, new JsonPath("properties", "status")).getName());
    NakshaCollection reordered = nativeCollection(definition(mapping("rating", MemberType.INT32), mapping("status")));
    assertEquals("cm01", memberForPath(reordered, new JsonPath("properties", "rating")).getName());
    assertEquals(indexNames(compiled), indexNames(reordered));
  }

  @Test void extendsXyzDefaultsAndLeavesConfigurationUnchanged() {
    NakshaCollection input = definition(mapping("score", MemberType.INT32));
    String original = Platform.toJSON(input);
    NakshaCollection compiled = nativeCollection(input);
    assertNotSame(input, compiled);
    assertEquals(original, Platform.toJSON(input));
    assertEquals("physical", compiled.getId());
    assertEquals("target", compiled.getCatalogId());
    assertFalse(compiled.hasRaw("customIndexMapping"));
    assertEquals(XyzMembers.ALL.size() + 1, compiled.getMembers().size());
    assertEquals(List.of("tags", "geo", "fn_nv", "ci01"), indexNames(compiled));
  }

  @Test void unmappedDefinitionsAreNormalizedInPlaceAsBefore() {
    NakshaCollection input = new NakshaCollection("plain");
    NakshaCollection result = nativeCollection(input);
    assertSame(input, result);
    assertEquals("physical", input.getId());
    assertEquals(List.of("tags", "geo", "fn_nv"), indexNames(input));
    assertNull(input.getMembers());
  }

  @Test void booleanMappingsCreateMembersWithoutIndices() {
    NakshaCollection compiled = nativeCollection(definition(mapping("score", MemberType.INT32), mapping("active", MemberType.BOOLEAN)));
    assertEquals(MemberType.BOOLEAN, compiled.getMembers().get("cm01").getDataType());
    assertEquals(MemberType.INT32, compiled.getMembers().get("cm02").getDataType());
    assertEquals(List.of("tags", "geo", "fn_nv", "ci02"), indexNames(compiled));
  }

  @Test void rejectsInvalidMappings() {
    CustomIndexMapping named = mapping("a");
    named.put("memberName", "user_name");
    List<NakshaCollection> invalid = List.of(
        definition(named), definition(mapping("a", MemberType.TUPLE_NUMBER)), definition(mapping("a", MemberType.INT8)),
        definition(mapping("a"), mapping("a")),
        definition(new CustomIndexMapping(new JsonPath("geometry"), MemberType.SPATIAL)));
    for (NakshaCollection definition : invalid) {
      NakshaException error = assertThrows(NakshaException.class, () -> nativeCollection(definition));
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.getError().getCode());
    }
  }

  private NakshaCollection fromJson(String raw) {
    String json = raw == null ? "{\"id\":\"mapped\"}" : "{\"id\":\"mapped\",\"customIndexMapping\":" + raw + "}";
    return Platform.javaProxy(Platform.fromJSON(json), NakshaCollection.class);
  }

  @Test void rejectsMalformedJson() {
    for (String raw : List.of("[null]", "[42]", "[{}]",
        "[{\"jsonPath\":\"properties.a\",\"dataType\":\"string\"}]",
        "[{\"jsonPath\":[\"properties\",\"a\"]}]", "[{\"jsonPath\":[],\"dataType\":\"string\"}]",
        "[{\"jsonPath\":[\"properties\",-1],\"dataType\":\"string\"}]",
        "[{\"jsonPath\":[\"properties\",true],\"dataType\":\"string\"}]",
        "[{\"jsonPath\":[\"properties\",\"a\"],\"dataType\":\"unknown\"}]")) {
      assertThrows(NakshaException.class, () -> nativeCollection(fromJson(raw)), raw);
    }
  }

  @Test void missingNullOrScalarMappingsAreAbsent() {
    for (String raw : Arrays.asList(null, "null", "42", "\"properties.a\"")) {
      NakshaCollection definition = fromJson(raw);
      assertFalse(CustomIndexMappingCompiler.hasMappings(definition), raw);
      assertSame(definition, nativeCollection(definition), raw);
      assertNull(definition.getMembers(), raw);
    }
  }

  @Test void wrongMappingContainersFailProxyBinding() {
    for (String raw : List.of("{}", "[[]]")) {
      assertThrows(IllegalArgumentException.class, () -> nativeCollection(fromJson(raw)), raw);
    }
  }

  @Test void capacityIncludesDefaultMembers() {
    int capacity = MemberList.MAX_MEMBERS - XyzMembers.ALL.size();
    List<CustomIndexMapping> mappings = new ArrayList<>();
    for (int i = 0; i < capacity; i++) mappings.add(mapping("p" + i));
    assertEquals(MemberList.MAX_MEMBERS, nativeCollection(definition(mappings.toArray(new CustomIndexMapping[0]))).getMembers().size());
    mappings.add(mapping("p" + capacity));
    assertThrows(NakshaException.class, () -> nativeCollection(definition(mappings.toArray(new CustomIndexMapping[0]))));
  }

  @Test void acceptsLiteralKeysAndArrayIndexes() {
    List<JsonPath> paths = List.of(new JsonPath("properties", ""), new JsonPath("properties", "*"),
        new JsonPath("properties", "items", 0), new JsonPath("properties", "items", "0"));
    CustomIndexMapping[] mappings = paths.stream().map(path -> new CustomIndexMapping(path, MemberType.STRING)).toArray(CustomIndexMapping[]::new);
    NakshaCollection compiled = nativeCollection(definition(mappings));
    for (JsonPath path : paths) assertNotNull(memberForPath(compiled, path));
  }
}
