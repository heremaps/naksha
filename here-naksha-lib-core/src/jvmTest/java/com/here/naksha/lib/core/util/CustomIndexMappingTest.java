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

  /** Validates the definition like a configuration save, the members and indices are applied when they are read. */
  private NakshaCollection compiled(NakshaCollection definition) {
    CustomIndexMappings.validate(definition);
    return definition;
  }

  private Member memberForPath(NakshaCollection compiled, JsonPath path) {
    return compiled.getMembers().stream()
        .filter(member -> member != null && CustomIndexMappings.samePath(member.getPath(), path))
        .findFirst().orElse(null);
  }

  private List<String> indexNames(NakshaCollection definition) {
    return definition.getIndices().stream().map(Index::getName).collect(Collectors.toList());
  }

  @Test void namesFollowSortedPaths() {
    NakshaCollection compiled = compiled(definition(mapping("status"), mapping("rating", MemberType.INT32)));
    assertEquals("cm01", memberForPath(compiled, new JsonPath("properties", "rating")).getName());
    assertEquals("cm02", memberForPath(compiled, new JsonPath("properties", "status")).getName());
    NakshaCollection reordered = compiled(definition(mapping("rating", MemberType.INT32), mapping("status")));
    assertEquals("cm01", memberForPath(reordered, new JsonPath("properties", "rating")).getName());
    assertEquals(indexNames(compiled), indexNames(reordered));
  }

  @Test void appliesMappingsOnlyWhenMembersOrIndicesAreRead() {
    NakshaCollection input = definition(mapping("score", MemberType.INT32));
    String original = Platform.toJSON(input);
    // Configuration handling (validation, mapping checks) doesn't change the definition.
    CustomIndexMappings.validate(input);
    assertTrue(CustomIndexMappings.hasMappings(input));
    assertEquals(original, Platform.toJSON(input));
    // Reading the indices or members applies the XYZ members, the slim indices and the generated ones.
    assertEquals(List.of("tags", "geo", "fn_nv", "ci01"), indexNames(input));
    assertEquals(XyzMembers.ALL.size() + 1, input.getMembers().size());
    assertTrue(input.hasRaw("customIndexMapping"));
    // Applying again changes nothing.
    String applied = Platform.toJSON(input);
    input.getMembers();
    input.getIndices();
    assertEquals(applied, Platform.toJSON(input));
  }

  @Test void keepsExplicitMembersAndIndices() {
    NakshaCollection input = definition(mapping("score", MemberType.INT32));
    input.setMembers(new MemberList(new Member("speed", MemberType.INT32, new JsonPath("properties", "speed"))));
    input.setIndices(new IndexList(new Index("speed_idx", "speed")));
    assertEquals(List.of("speed", "cm01"), input.getMembers().stream().map(Member::getName).collect(Collectors.toList()));
    assertEquals(List.of("speed_idx", "ci01"), indexNames(input));
  }

  @Test void unmappedDefinitionsAreNotChanged() {
    NakshaCollection input = new NakshaCollection("plain");
    assertNull(input.getMembers());
    assertNull(input.getIndices());
  }

  @Test void invalidMappingsFailOnSchemaAccess() {
    NakshaCollection input = definition(mapping("a"), mapping("a"));
    String original = Platform.toJSON(input);
    assertThrows(NakshaException.class, input::getMembers);
    assertThrows(NakshaException.class, input::getIndices);
    assertEquals(original, Platform.toJSON(input));
  }

  @Test void generatedNamesAreReusedOnlyForTheSameDefinition() {
    // An existing cm01 for another property must not be taken as the member of properties.score.
    NakshaCollection otherPath = definition(mapping("score", MemberType.INT32));
    otherPath.setMembers(new MemberList(new Member("cm01", MemberType.INT32, new JsonPath("properties", "speed"))));
    assertThrows(NakshaException.class, () -> CustomIndexMappings.validate(otherPath));
    assertThrows(NakshaException.class, otherPath::getMembers);
    NakshaCollection otherType = definition(mapping("score", MemberType.INT32));
    otherType.setMembers(new MemberList(new Member("cm01", MemberType.STRING, new JsonPath("properties", "score"))));
    assertThrows(NakshaException.class, () -> CustomIndexMappings.validate(otherType));
    NakshaCollection otherIndex = definition(mapping("score", MemberType.INT32));
    otherIndex.setIndices(new IndexList(new Index("ci01", "id")));
    assertThrows(NakshaException.class, () -> CustomIndexMappings.validate(otherIndex));
    // The same definitions are reused, e.g. for a collection read back from the storage.
    NakshaCollection same = definition(mapping("score", MemberType.INT32));
    same.setMembers(new MemberList(new Member("cm01", MemberType.INT32, new JsonPath("properties", "score"))));
    same.setIndices(new IndexList(new Index("ci01", "cm01")));
    CustomIndexMappings.validate(same);
    assertEquals(List.of("ci01"), indexNames(same));
  }

  @Test void capacityIncludesMissingMandatoryMembers() {
    int capacity = MemberList.MAX_MEMBERS - StandardMembers.MANDATORY.size();
    List<CustomIndexMapping> mappings = new ArrayList<>();
    for (int i = 0; i < capacity; i++) mappings.add(mapping("p" + i));
    NakshaCollection fits = definition(mappings.toArray(new CustomIndexMapping[0]));
    fits.setMembers(new MemberList());
    CustomIndexMappings.validate(fits);
    assertEquals(MemberList.MAX_MEMBERS, fits.useMembers().size());
    mappings.add(mapping("p" + capacity));
    NakshaCollection tooMany = definition(mappings.toArray(new CustomIndexMapping[0]));
    tooMany.setMembers(new MemberList());
    assertThrows(NakshaException.class, () -> CustomIndexMappings.validate(tooMany));
  }

  @Test void appliedMappingsAreNotProcessedAgain() {
    NakshaCollection input = definition(mapping("score", MemberType.INT32));
    MemberList members = input.getMembers();
    // Mappings don't change in place, a later read uses the result of the first application.
    input.getCustomIndexMapping().add(mapping("score", MemberType.INT32));
    assertSame(members, input.getMembers());
    // Replacing the mappings applies them again.
    input.setCustomIndexMapping(new CustomIndexMappingList(mapping("score", MemberType.INT32), mapping("score", MemberType.INT32)));
    assertThrows(NakshaException.class, input::getMembers);
  }

  @Test void booleanMappingsCreateMembersWithoutIndices() {
    NakshaCollection compiled = compiled(definition(mapping("score", MemberType.INT32), mapping("active", MemberType.BOOLEAN)));
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
        definition(new CustomIndexMapping(new JsonPath("geometry"), MemberType.SPATIAL)),
        // Only types that are fully translated into member queries can be mapped.
        definition(mapping("shape", MemberType.SPATIAL)), definition(mapping("blob", MemberType.BYTE_ARRAY)));
    for (NakshaCollection definition : invalid) {
      NakshaException error = assertThrows(NakshaException.class, () -> compiled(definition));
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
      assertThrows(NakshaException.class, () -> compiled(fromJson(raw)), raw);
    }
  }

  @Test void missingNullOrScalarMappingsAreAbsent() {
    for (String raw : Arrays.asList(null, "null", "42", "\"properties.a\"")) {
      NakshaCollection definition = fromJson(raw);
      assertFalse(CustomIndexMappings.hasMappings(definition), raw);
      assertNull(definition.getMembers(), raw);
    }
  }

  @Test void wrongMappingContainersFailProxyBinding() {
    for (String raw : List.of("{}", "[[]]")) {
      assertThrows(IllegalArgumentException.class, () -> compiled(fromJson(raw)), raw);
    }
  }

  @Test void capacityIncludesDefaultMembers() {
    int capacity = MemberList.MAX_MEMBERS - XyzMembers.ALL.size();
    List<CustomIndexMapping> mappings = new ArrayList<>();
    for (int i = 0; i < capacity; i++) mappings.add(mapping("p" + i));
    assertEquals(MemberList.MAX_MEMBERS, compiled(definition(mappings.toArray(new CustomIndexMapping[0]))).getMembers().size());
    mappings.add(mapping("p" + capacity));
    assertThrows(NakshaException.class, () -> compiled(definition(mappings.toArray(new CustomIndexMapping[0]))));
  }

  @Test void acceptsLiteralKeysAndArrayIndexes() {
    List<JsonPath> paths = List.of(new JsonPath("properties", ""), new JsonPath("properties", "*"),
        new JsonPath("properties", "items", 0), new JsonPath("properties", "items", "0"));
    CustomIndexMapping[] mappings = paths.stream().map(path -> new CustomIndexMapping(path, MemberType.STRING)).toArray(CustomIndexMapping[]::new);
    NakshaCollection compiled = compiled(definition(mappings));
    for (JsonPath path : paths) assertNotNull(memberForPath(compiled, path));
  }
}
