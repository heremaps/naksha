package com.here.naksha.lib.core.util;


import naksha.model.objects.CustomIndexMappingUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.base.Platform;
import naksha.model.objects.JsonPath;
import naksha.model.objects.Member;
import naksha.model.objects.MemberType;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.StandardMembers;
import naksha.model.request.PropertyFilter;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ResultFilter;
import naksha.model.request.ResultFilterList;
import naksha.model.request.ops.*;
import naksha.model.request.query.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Complete-expression translation only. The request is rewritten in place, a null result means it is unchanged. */
public final class PropertyMemberQueryTranslator {
  // TODO: INT8 equality and comparisons fail in lib-psql's PgQueryWhereBuilder: an INT8 member only accepts Byte
  //       operands, but placeholderForArg rejects Byte; null checks bind no operand and work. The mapping validation
  //       rejects INT8 for now.
  private static final List<MemberType> NUMERIC_TYPES = List.of(
      MemberType.INT8, MemberType.INT16, MemberType.INT32, MemberType.INT64, MemberType.FLOAT32, MemberType.FLOAT64);
  /** Member types whose null checks and value queries are translated. */
  private static final List<MemberType> TRANSLATED_TYPES = List.of(
      MemberType.BOOLEAN, MemberType.STRING, MemberType.INT8, MemberType.INT16, MemberType.INT32, MemberType.INT64,
      MemberType.FLOAT32, MemberType.FLOAT64, MemberType.TAG_LIST, MemberType.TAG_MAP);
  private static final Logger logger = LoggerFactory.getLogger(PropertyMemberQueryTranslator.class);

  private PropertyMemberQueryTranslator() {}

  /**
   * Moves the legacy property query of the request onto the resolved members of the given collection, so the storage can
   * filter in SQL and use the collection's indices.
   * @return the rewritten request; null, if the request stays unchanged on the legacy path.
   */
  public static @Nullable ReadFeatures adapt(ReadFeatures request, NakshaCollection collection) {
    if (request.getQueryMembers() != null) return null;
    // Nothing to translate: other criteria (tags, spatial, feature ids) are converted by the storage on the legacy path.
    IPropertyQuery expression = request.getQuery().getProperties();
    if (expression == null) return null;
    // All or nothing: one untranslatable predicate keeps the whole request on the legacy path.
    Op propertyOp = translate(expression, collection.useMembers());
    if (propertyOp == null) return null;
    return replaceLegacyCriteria(request, propertyOp);
  }

  /**
   * Replaces the legacy criteria of the request (property query, tags, spatial, feature ids) and its in-memory property
   * filter with a single member query, because the storage doesn't accept legacy criteria next to member queries.
   * @return the rewritten request; null, if a legacy criterion can't be converted, the request is then unchanged.
   */
  private static @Nullable ReadFeatures replaceLegacyCriteria(ReadFeatures request, Op propertyOp) {
    // Convert before changing anything, so that a failed conversion leaves the request unchanged.
    Op other;
    try {
      other = QueryConverter.PgQueryConverter_C.convert(request.getQuery());
    } catch (NakshaException error) {
      if (NakshaError.UNSUPPORTED_OPERATION.equals(error.getError().getCode())) return null;
      throw error;
    }
    List<Op> predicates = new ArrayList<>();
    predicates.add(propertyOp);
    if (other != null) predicates.add(other);
    // The storage converts feature ids itself only for legacy requests (see PgQueryWhereBuilder.build).
    if (!request.getFeatureIds().isEmpty()) {
      predicates.add(new IsAnyOf(StandardMembers.Id, request.getFeatureIds().toArray(new String[0])));
    }
    // The member query replaces the in-memory check of the property query, other result filters are kept. This runs
    // before 'query' is removed, because a proxy wrapper (bbox, tile) matches the filter of its wrapped request by value.
    ResultFilterList filters = new ResultFilterList();
    for (ResultFilter filter : request.getResultFilters()) {
      if (!(filter instanceof PropertyFilter && request.equals(((PropertyFilter) filter).getReq()))) filters.add(filter);
    }
    request.setResultFilters(filters);
    // Remove the legacy syntax, it is now part of the member query (properties, tags, spatial and feature ids).
    request.removeRaw("query");
    request.getFeatureIds().clear();
    request.setQueryMembers(predicates.size() == 1 ? predicates.get(0) : new And(predicates.toArray(new Op[0])));
    return request;
  }

  private static @Nullable Op translate(IPropertyQuery query, List<Member> members) {
    if (query instanceof PNot) {
      Op child = translate(((PNot) query).getQuery(), members);
      return child == null ? null : new Not(child);
    }
    if (query instanceof PAnd) return translateChildren((PAnd) query, members, true);
    if (query instanceof POr) {
      // REST sends "cs={...}" as the object OR the object wrapped into an array, for a tag-map only the object fits.
      PQuery object = objectContainment((POr) query);
      if (object != null) {
        Member member = exactTagMap(object.getProperty().getPath(), members);
        if (member != null) return tagMapContains(member, (String) object.getValue());
      }
      return translateChildren((POr) query, members, false);
    }
    if (query instanceof PQuery) return translateLeaf((PQuery) query, members);
    return null;
  }

  /** Returns the object query of an OR shaped like the "cs={...}" expression of PropertyQueryUtil, otherwise null. */
  private static @Nullable PQuery objectContainment(POr or) {
    if (or.size() != 2 || !(or.get(0) instanceof PQuery) || !(or.get(1) instanceof PQuery)) return null;
    PQuery object = (PQuery) or.get(0);
    PQuery array = (PQuery) or.get(1);
    if (!AnyOp.CONTAINS.equals(object.getOp()) || !AnyOp.CONTAINS.equals(array.getOp())) return null;
    if (!(object.getValue() instanceof String) || !("[" + object.getValue() + "]").equals(array.getValue())) return null;
    if (!CustomIndexMappingUtil.samePath(object.getProperty().getPath(), array.getProperty().getPath())) return null;
    return object;
  }

  private static @Nullable Op translateChildren(
      List<IPropertyQuery> children, List<Member> members, boolean and) {
    if (children.isEmpty()) return null;
    List<Op> translated = new ArrayList<>();
    for (IPropertyQuery child : children) {
      if (child == null) return null;
      Op op = translate(child, members);
      if (op == null) return null;
      translated.add(op);
    }
    Op[] array = translated.toArray(new Op[0]);
    return and ? new And(array) : new Or(array);
  }

  /** Returns the member of exactly the given path or, for a tag-map, of the parent of a direct key. */
  private static @Nullable Member memberFor(JsonPath path, List<Member> members) {
    for (Member member : members) {
      if (CustomIndexMappingUtil.samePath(member.getPath(), path)) return member;
      if (MemberType.TAG_MAP.equals(member.getDataType()) && isDirectKey(member.getPath(), path)) return member;
    }
    return null;
  }

  private static boolean isDirectKey(JsonPath mapped, JsonPath path) {
    if (path.size() != mapped.size() + 1 || !(path.get(mapped.size()) instanceof String)) return false;
    for (int i = 0; i < mapped.size(); i++) {
      if (!Objects.equals(mapped.get(i), path.get(i))) return false;
    }
    return true;
  }

  private static @Nullable Member exactTagMap(JsonPath path, List<Member> members) {
    Member member = memberFor(path, members);
    return member != null && MemberType.TAG_MAP.equals(member.getDataType())
        && CustomIndexMappingUtil.samePath(member.getPath(), path) ? member : null;
  }

  private static @Nullable Op translateLeaf(PQuery query, List<Member> members) {
    JsonPath path = query.getProperty().getPath();
    Member member = memberFor(path, members);
    if (member == null) return null;
    MemberType type = member.getDataType();
    // An unhandled type keeps the whole property expression on PropertyFilter.
    // Property filtering then runs after the storage read, which may increase
    // feature loading and retains legacy comparison and limit behavior.
    if (!TRANSLATED_TYPES.contains(type)) {
      logger.warn("Member type {} of {} is not supported by PropertyMemberQueryTranslator, using the legacy path", type, member.getName());
      return null;
    }
    AnyOp op = query.getOp();
    Object value = query.getValue();
    if (path.size() > member.getPath().size()) {
      return MemberType.TAG_MAP.equals(type) ? translateTagKey(member, (String) path.get(path.size() - 1), op, value) : null;
    }
    if (AnyOp.IS_NULL.equals(op)) return new IsNull(member);
    if (AnyOp.EXISTS.equals(op) || AnyOp.IS_NOT_NULL.equals(op)) return new Not(new IsNull(member));
    if (MemberType.BOOLEAN.equals(type)) {
      // REST encodes boolean equality in the operation, only CONTAINS carries the boolean as value.
      if (AnyOp.IS_TRUE.equals(op) || (AnyOp.CONTAINS.equals(op) && Boolean.TRUE.equals(value))) return new IsTrue(member);
      if (AnyOp.IS_FALSE.equals(op) || (AnyOp.CONTAINS.equals(op) && Boolean.FALSE.equals(value))) return new IsFalse(member);
      return null;
    }
    if (MemberType.TAG_LIST.equals(type)) {
      return AnyOp.CONTAINS.equals(op) && value instanceof String ? tagListContains(member, (String) value) : null;
    }
    if (MemberType.TAG_MAP.equals(type)) {
      return AnyOp.CONTAINS.equals(op) && value instanceof String ? tagMapContains(member, (String) value) : null;
    }
    if (MemberType.STRING.equals(type)) {
      if (!(value instanceof String)) return null;
      if (StringOp.EQUALS.equals(op) || AnyOp.CONTAINS.equals(op)) return new Equals(member, value);
      if (StringOp.STARTS_WITH.equals(op)) return new StartsWith(member, (String) value);
      return null;
    }
    if (!NUMERIC_TYPES.contains(type) || !(value instanceof Number)) return null;
    if (DoubleOp.EQ.equals(op) || AnyOp.CONTAINS.equals(op)) return new Equals(member, value);
    if (DoubleOp.GT.equals(op)) return new Gt(member, value);
    if (DoubleOp.GTE.equals(op)) return new Gte(member, value);
    if (DoubleOp.LT.equals(op)) return new Lt(member, value);
    if (DoubleOp.LTE.equals(op)) return new Lte(member, value);
    return null;
  }

  private static @Nullable Op translateTagKey(Member member, String key, AnyOp op, @Nullable Object value) {
    if (AnyOp.EXISTS.equals(op)) return new TagMapHasKey(member, key);
    if (AnyOp.IS_NULL.equals(op)) return new TagIsNull(member, key);
    if (AnyOp.IS_NOT_NULL.equals(op)) return new Not(new TagIsNull(member, key));
    if (AnyOp.IS_TRUE.equals(op)) return new TagEquals(member, key, true);
    if (AnyOp.IS_FALSE.equals(op)) return new TagEquals(member, key, false);
    if (AnyOp.CONTAINS.equals(op)) {
      return value instanceof String || value instanceof Number || value instanceof Boolean
          ? new TagEquals(member, key, value) : null;
    }
    if (value instanceof String) {
      if (StringOp.EQUALS.equals(op)) return new TagEquals(member, key, value);
      if (StringOp.STARTS_WITH.equals(op)) return new TagStartsWith(member, key, (String) value);
      return null;
    }
    if (!(value instanceof Number)) return null;
    if (DoubleOp.EQ.equals(op)) return new TagEquals(member, key, value);
    if (DoubleOp.GT.equals(op)) return new TagGt(member, key, value);
    if (DoubleOp.GTE.equals(op)) return new TagGte(member, key, value);
    if (DoubleOp.LT.equals(op)) return new TagLt(member, key, value);
    if (DoubleOp.LTE.equals(op)) return new TagLte(member, key, value);
    return null;
  }

  /** A plain string is a single tag, a JSON array of strings requires all of its tags. */
  private static @Nullable Op tagListContains(Member member, String value) {
    String trimmed = value.trim();
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return new TagListContains(member, value);
    Object parsed = parseJson(trimmed);
    if (!(parsed instanceof List)) return null;
    List<?> items = (List<?>) parsed;
    for (Object item : items) {
      if (!(item instanceof String)) return null;
    }
    return new TagListContainsAllOf(member, items.toArray());
  }

  /** A flat JSON object requires each of its entries, nested objects and arrays are not supported. */
  private static @Nullable Op tagMapContains(Member member, String value) {
    Object parsed = parseJson(value);
    if (!(parsed instanceof Map)) return null;
    Map<?, ?> entries = (Map<?, ?>) parsed;
    if (entries.isEmpty()) return new Not(new IsNull(member));
    List<Op> equals = new ArrayList<>();
    for (Map.Entry<?, ?> entry : entries.entrySet()) {
      Object item = entry.getValue();
      if (item != null && !(item instanceof String || item instanceof Number || item instanceof Boolean)) return null;
      equals.add(new TagEquals(member, (String) entry.getKey(), item));
    }
    return equals.size() == 1 ? equals.get(0) : new And(equals.toArray(new Op[0]));
  }

  /** Returns the parsed JSON or null if parsing fails. */
  private static @Nullable Object parseJson(String text) {
    try {
      return Platform.fromJSON(text);
    } catch (Exception e) {
      return null;
    }
  }
}
