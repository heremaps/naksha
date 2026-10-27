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
    CustomIndexMappingUtil.validate(definition);
    return definition;
  }

  private Member memberForPath(NakshaCollection compiled, JsonPath path) {
    return compiled.useMembers().stream()
        .filter(member -> member != null && CustomIndexMappingUtil.samePath(member.getPath(), path))
        .findFirst().orElse(null);
  }

  private List<String> indexNames(NakshaCollection definition) {
    definition.useMembers();
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

  @Test void appliesMappingsOnlyInUseMembers() {
    NakshaCollection input = definition(mapping("score", MemberType.INT32));
    String original = Platform.toJSON(input);
    // Configuration handling and the declared members and indices don't change the definition.
    CustomIndexMappingUtil.validate(input);
    assertTrue(CustomIndexMappingUtil.hasMappings(input));
    assertNull(input.getMembers());
    assertNull(input.getIndices());
    assertEquals(original, Platform.toJSON(input));
    // useMembers applies the XYZ and mandatory members, the slim indices and the generated ones.
    MemberList members = input.useMembers();
    assertEquals("cm01", memberForPath(input, new JsonPath("properties", "score")).getName());
    assertSame(members, input.getMembers());
    assertEquals(List.of("tags", "geo", "fn_nv", "ci01"), indexNames(input));
    assertTrue(input.hasRaw("customIndexMapping"));
    // Applying again changes nothing.
    String applied = Platform.toJSON(input);
    input.useMembers();
    assertEquals(applied, Platform.toJSON(input));
  }

  @Test void replacedIndicesAreCompletedAgain() {
    NakshaCollection input = definition(mapping("score", MemberType.INT32));
    input.useMembers();
    input.withMinimalIndices();
    input.useMembers();
    assertEquals(List.of("ci01"), indexNames(input));
    NakshaCollection reparsed = Platform.javaProxy(Platform.fromJSON(Platform.toJSON(input)), NakshaCollection.class);
    assertEquals(indexNames(reparsed), indexNames(input));
    // A conflicting index replaced after the first use is rejected as well.
    input.setIndices(new IndexList(new Index("ci01", "id")));
    NakshaException error = assertThrows(NakshaException.class, input::useMembers);
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.getError().getCode());
  }

  @Test void keepsExplicitMembersAndIndices() {
    NakshaCollection input = definition(mapping("score", MemberType.INT32));
    input.setMembers(new MemberList(new Member("speed", MemberType.INT32, new JsonPath("properties", "speed"))));
    input.setIndices(new IndexList(new Index("speed_idx", "speed")));
    assertEquals(List.of("speed", "cm01"), input.useMembers().stream().limit(2).map(Member::getName).collect(Collectors.toList()));
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
    assertThrows(NakshaException.class, input::useMembers);
    assertEquals(original, Platform.toJSON(input));
  }

  @Test void generatedNamesAreReusedOnlyForTheSameDefinition() {
    // An existing cm01 for another property must not be taken as the member of properties.score.
    NakshaCollection otherPath = definition(mapping("score", MemberType.INT32));
    otherPath.setMembers(new MemberList(new Member("cm01", MemberType.INT32, new JsonPath("properties", "speed"))));
    assertThrows(NakshaException.class, () -> CustomIndexMappingUtil.validate(otherPath));
    assertThrows(NakshaException.class, otherPath::useMembers);
    NakshaCollection otherType = definition(mapping("score", MemberType.INT32));
    otherType.setMembers(new MemberList(new Member("cm01", MemberType.STRING, new JsonPath("properties", "score"))));
    assertThrows(NakshaException.class, () -> CustomIndexMappingUtil.validate(otherType));
    NakshaCollection otherIndex = definition(mapping("score", MemberType.INT32));
    otherIndex.setIndices(new IndexList(new Index("ci01", "id")));
    assertThrows(NakshaException.class, () -> CustomIndexMappingUtil.validate(otherIndex));
    // The same definitions are reused, e.g. for a collection read back from the storage.
    NakshaCollection same = definition(mapping("score", MemberType.INT32));
    same.setMembers(new MemberList(new Member("cm01", MemberType.INT32, new JsonPath("properties", "score"))));
    same.setIndices(new IndexList(new Index("ci01", "cm01")));
    CustomIndexMappingUtil.validate(same);
    assertEquals(List.of("ci01"), indexNames(same));
  }

  @Test void capacityIncludesMissingMandatoryMembers() {
    int capacity = MemberList.MAX_MEMBERS - StandardMembers.MANDATORY.size();
    List<CustomIndexMapping> mappings = new ArrayList<>();
    for (int i = 0; i < capacity; i++) mappings.add(mapping("p" + i));
    NakshaCollection fits = definition(mappings.toArray(new CustomIndexMapping[0]));
    fits.setMembers(new MemberList());
    CustomIndexMappingUtil.validate(fits);
    assertEquals(MemberList.MAX_MEMBERS, fits.useMembers().size());
    mappings.add(mapping("p" + capacity));
    NakshaCollection tooMany = definition(mappings.toArray(new CustomIndexMapping[0]));
    tooMany.setMembers(new MemberList());
    assertThrows(NakshaException.class, () -> CustomIndexMappingUtil.validate(tooMany));
  }

  @Test void appliedMappingsAreNotProcessedAgain() {
    NakshaCollection input = definition(mapping("score", MemberType.INT32));
    MemberList members = input.useMembers();
    // Mappings don't change in place, a later read uses the result of the first application.
    input.getCustomIndexMapping().add(mapping("score", MemberType.INT32));
    assertSame(members, input.useMembers());
    // Replacing the mappings applies them again.
    input.setCustomIndexMapping(new CustomIndexMappingList(mapping("score", MemberType.INT32), mapping("score", MemberType.INT32)));
    assertThrows(NakshaException.class, input::useMembers);
  }

  @Test void booleanMappingsCreateMembersWithoutIndices() {
    NakshaCollection compiled = compiled(definition(mapping("score", MemberType.INT32), mapping("active", MemberType.BOOLEAN)));
    assertEquals(MemberType.BOOLEAN, compiled.useMembers().get("cm01").getDataType());
    assertEquals(MemberType.INT32, compiled.useMembers().get("cm02").getDataType());
    assertEquals(List.of("tags", "geo", "fn_nv", "ci02"), indexNames(compiled));
  }

  @Test void ignoresUnknownMappingFields() {
    CustomIndexMapping named = mapping("score", MemberType.INT32);
    named.put("memberName", "user_name");
    NakshaCollection definition = compiled(definition(named));
    assertEquals("cm01", memberForPath(definition, new JsonPath("properties", "score")).getName());
    assertNull(definition.useMembers().get("user_name"));
  }

  @Test void rejectsInvalidMappings() {
    List<NakshaCollection> invalid = List.of(
        definition(mapping("a", MemberType.TUPLE_NUMBER)), definition(mapping("a", MemberType.INT8)),
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
      assertFalse(CustomIndexMappingUtil.hasMappings(definition), raw);
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
    assertEquals(MemberList.MAX_MEMBERS, compiled(definition(mappings.toArray(new CustomIndexMapping[0]))).useMembers().size());
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
