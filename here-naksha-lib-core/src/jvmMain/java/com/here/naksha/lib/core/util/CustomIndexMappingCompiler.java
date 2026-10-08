package com.here.naksha.lib.core.util;

import static com.here.naksha.lib.core.models.naksha.CustomIndexMappings.getCustomIndexMapping;

import com.here.naksha.lib.core.models.naksha.CustomIndexMapping;
import com.here.naksha.lib.core.models.naksha.CustomIndexMappingList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.base.PlatformList;
import naksha.base.PlatformListApi;
import naksha.base.PlatformMap;
import naksha.model.objects.Index;
import naksha.model.objects.JsonPath;
import naksha.model.objects.Member;
import naksha.model.objects.MemberList;
import naksha.model.objects.MemberType;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.Nullable;

/** Validates mappings and expands them into deterministic native members (cmNN) and indices (ciNN). */
public final class CustomIndexMappingCompiler {
  private static final List<MemberType> SUPPORTED_TYPES = List.of(
      MemberType.BOOLEAN, MemberType.STRING, MemberType.INT16, MemberType.INT32, MemberType.INT64,
      MemberType.FLOAT32, MemberType.FLOAT64, MemberType.BYTE_ARRAY, MemberType.SPATIAL,
      MemberType.TAG_MAP, MemberType.TAG_LIST);

  private CustomIndexMappingCompiler() {}

  public static boolean hasMappings(@Nullable NakshaCollection definition) {
    if (definition == null) return false;
    CustomIndexMappingList mappings = mappingList(definition);
    return mappings != null && !mappings.isEmpty();
  }

  /** Validates the public mapping shape without changing the definition. */
  public static void validate(NakshaCollection definition) {
    validatedMappings(definition);
  }

  /** Adds the generated members and indices to the given, caller-owned definition. */
  public static NakshaCollection expand(NakshaCollection definition) {
    List<CustomIndexMapping> mappings = validatedMappings(definition);
    mappings.sort((a, b) -> comparePaths(a.getJsonPath(), b.getJsonPath()));
    if (mappings.isEmpty()) return definition;
    MemberList members = definition.useMembers();
    for (int i = 0; i < mappings.size(); i++) {
      CustomIndexMapping mapping = mappings.get(i);
      String suffix = (i + 1 < 10 ? "0" : "") + (i + 1);
      Member member = new Member("cm" + suffix, mapping.getDataType(), mapping.getJsonPath());
      for (Member other : members) {
        if (other != null && !other.isVirtual()
            && (isPrefix(other.getPath(), member.getPath()) || isPrefix(member.getPath(), other.getPath()))) {
          throw invalid("customIndexMapping", "path " + member.getPath() + " overlaps member '" + other.getName() + "'");
        }
      }
      definition.addMember(member);
      // Native storage supports boolean members, but no indices on them.
      if (!MemberType.BOOLEAN.equals(member.getDataType())) {
        definition.addIndex(new Index("ci" + suffix, member.getName()));
      }
    }
    return definition;
  }

  /** Returns the non-virtual member materializing exactly the given path. */
  public static @Nullable Member memberForPath(NakshaCollection definition, JsonPath path) {
    MemberList members = definition.getMembers();
    if (members != null) {
      for (Member member : members) {
        if (member != null && !member.isVirtual() && samePath(member.getPath(), path)) return member;
      }
    }
    return null;
  }

  private static @Nullable CustomIndexMappingList mappingList(NakshaCollection definition) {
    Object raw = definition.getRaw("customIndexMapping");
    if (raw != null && !(raw instanceof PlatformList)) throw invalid("customIndexMapping", "must be an array");
    if (raw instanceof PlatformList) {
      PlatformList list = (PlatformList) raw;
      for (int i = 0; i < PlatformListApi.array_get_length(list); i++) {
        if (!(PlatformListApi.array_get(list, i) instanceof PlatformMap)) {
          throw invalid("customIndexMapping[" + i + "]", "must be an object");
        }
      }
    }
    return getCustomIndexMapping(definition);
  }

  private static List<CustomIndexMapping> validatedMappings(NakshaCollection definition) {
    List<CustomIndexMapping> result = new ArrayList<>();
    CustomIndexMappingList mappings = mappingList(definition);
    if (mappings == null) return result;
    for (int i = 0; i < mappings.size(); i++) {
      String field = "customIndexMapping[" + i + "]";
      CustomIndexMapping mapping = mappings.get(i);
      if (mapping == null) throw invalid(field, "must not be null");
      for (String key : mapping.keySet()) {
        if (!"jsonPath".equals(key) && !"dataType".equals(key)) {
          throw invalid(field + "." + key, "unknown field; names are generated internally");
        }
      }
      MemberType type = mapping.getDataType();
      if (type == null) throw invalid(field + ".dataType", "is required");
      if (!SUPPORTED_TYPES.contains(type)) throw invalid(field + ".dataType", "unsupported member type '" + type + "'");
      JsonPath path = mapping.getJsonPath();
      if (path == null) throw invalid(field + ".jsonPath", "is required");
      if (path.isEmpty()) throw invalid(field + ".jsonPath", "must not be empty");
      try {
        path.validate();
      } catch (NakshaException error) {
        throw invalid(field + ".jsonPath", error.getError().getMsg());
      }
      for (Object segment : path) {
        if (segment instanceof Integer && (Integer) segment < 0) {
          throw invalid(field + ".jsonPath", "array indexes must be nonnegative");
        }
      }
      result.add(mapping);
    }
    for (int i = 0; i < result.size(); i++) {
      for (int j = 0; j < i; j++) {
        JsonPath a = result.get(j).getJsonPath();
        JsonPath b = result.get(i).getJsonPath();
        // A value below another mapped value is materialized inconsistently, see the overlap test.
        if (isPrefix(a, b) || isPrefix(b, a)) throw invalid("customIndexMapping", "paths " + a + " and " + b + " overlap");
      }
    }
    return result;
  }

  private static boolean isPrefix(JsonPath prefix, JsonPath path) {
    if (prefix.size() > path.size()) return false;
    for (int i = 0; i < prefix.size(); i++) {
      if (!Objects.equals(prefix.get(i), path.get(i))) return false;
    }
    return true;
  }

  static int comparePaths(JsonPath a, JsonPath b) {
    for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
      Object left = a.get(i);
      Object right = b.get(i);
      int difference;
      if (left instanceof String && right instanceof String) difference = ((String) left).compareTo((String) right);
      else if (left instanceof Integer && right instanceof Integer) difference = Integer.compare((Integer) left, (Integer) right);
      else difference = left instanceof String ? -1 : 1;
      if (difference != 0) return difference;
    }
    return Integer.compare(a.size(), b.size());
  }

  static boolean samePath(JsonPath a, JsonPath b) {
    return a.size() == b.size() && isPrefix(a, b);
  }

  private static NakshaException invalid(String field, String detail) {
    return new NakshaException(NakshaError.ILLEGAL_ARGUMENT, field + ": " + detail);
  }
}
