package com.here.naksha.lib.core.util;


import naksha.model.objects.CustomIndexMapping;
import naksha.model.objects.CustomIndexMappingList;
import java.util.ArrayList;
import java.util.List;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.objects.JsonPath;
import naksha.model.objects.Member;
import naksha.model.objects.MemberType;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.StandardMembers;
import naksha.model.request.PropertyFilter;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ResultFilterList;
import naksha.model.request.ops.*;
import naksha.model.request.query.*;
import org.jetbrains.annotations.Nullable;

/** Complete-expression translation only. A null result means keep the original request unchanged. */
public final class PropertyMemberQueryTranslator {
  private PropertyMemberQueryTranslator() {}

  public static @Nullable ReadFeatures adapt(ReadFeatures original, NakshaCollection schema) {
    return adapt(original, schema, schema);
  }

  public static @Nullable ReadFeatures adapt(
      ReadFeatures original, NakshaCollection schema, NakshaCollection configured) {
    // Member queries describe single HEAD states; history reads (e.g. the activity log) stay on the legacy path.
    if (original.getQueryMembers() != null || original.getQueryHistory() || original.getVersions() != 1) return null;
    // Reading "query" would create it, so check first to never modify the original request.
    IPropertyQuery expression = original.hasRaw("query") ? original.getQuery().getProperties() : null;
    if (expression == null) return null;
    Op propertyOp = translate(expression, schema, configured);
    if (propertyOp == null) return null;
    ReadFeatures request = original.copy(true);
    Op other;
    try {
      // A raw copy can't restore the typed tag and spatial queries, take them from the original.
      request.getQuery().setTags(original.getQuery().getTags());
      request.getQuery().setSpatial(original.getQuery().getSpatial());
      other = QueryConverter.PgQueryConverter_C.convert(request.getQuery());
    } catch (NakshaException error) {
      if (NakshaError.UNSUPPORTED_OPERATION.equals(error.getError().getCode())) return null;
      throw error;
    }
    List<Op> predicates = new ArrayList<>();
    predicates.add(propertyOp);
    if (other != null) predicates.add(other);
    if (!request.getFeatureIds().isEmpty()) {
      predicates.add(new IsAnyOf(StandardMembers.Id, request.getFeatureIds().toArray(new String[0])));
    }
    // The legacy filter stays as exact check of the results, on a typed copy of the property query.
    ReadFeatures captured = new ReadFeatures().withPropertyQuery(copyExpression(expression));
    ResultFilterList filters = new ResultFilterList();
    filters.add(new PropertyFilter(captured));
    request.setResultFilters(filters);
    request.removeRaw("query");
    request.getFeatureIds().clear();
    request.setQueryMembers(predicates.size() == 1 ? predicates.get(0) : new And(predicates.toArray(new Op[0])));
    return request;
  }

  private static IPropertyQuery copyExpression(IPropertyQuery query) {
    if (query instanceof PQuery) {
      PQuery leaf = (PQuery) query;
      List<Object> path = new ArrayList<>();
      for (Object segment : leaf.getProperty().getPath()) {
        if (segment != null) path.add(segment);
      }
      Property property = new Property();
      property.setPath(new JsonPath(path.toArray()));
      return new PQuery(property, leaf.getOp(), leaf.getValue());
    }
    if (query instanceof PAnd || query instanceof POr) {
      List<IPropertyQuery> children = query instanceof PAnd ? (PAnd) query : (POr) query;
      List<IPropertyQuery> copy = new ArrayList<>();
      for (IPropertyQuery child : children) {
        if (child != null) copy.add(copyExpression(child));
      }
      IPropertyQuery[] array = copy.toArray(new IPropertyQuery[0]);
      return query instanceof PAnd ? new PAnd(array) : new POr(array);
    }
    throw new IllegalStateException("Only translated expressions can be captured");
  }

  public static @Nullable Op translate(IPropertyQuery query, NakshaCollection schema) {
    return translate(query, schema, schema);
  }

  public static @Nullable Op translate(IPropertyQuery query, NakshaCollection schema, NakshaCollection configured) {
    if (query instanceof PAnd) return translateChildren((PAnd) query, schema, configured, true);
    if (query instanceof POr) return translateChildren((POr) query, schema, configured, false);
    if (query instanceof PQuery) return translateLeaf((PQuery) query, schema, configured);
    return null;
  }

  private static @Nullable Op translateChildren(
      List<IPropertyQuery> children, NakshaCollection schema, NakshaCollection configured, boolean and) {
    if (children.isEmpty()) return null;
    List<Op> translated = new ArrayList<>();
    for (IPropertyQuery child : children) {
      if (child == null) return null;
      Op op = translate(child, schema, configured);
      if (op == null) return null;
      translated.add(op);
    }
    Op[] array = translated.toArray(new Op[0]);
    return and ? new And(array) : new Or(array);
  }

  private static @Nullable Op translateLeaf(PQuery query, NakshaCollection schema, NakshaCollection configured) {
    CustomIndexMappingList mappings = configured.getCustomIndexMapping();
    CustomIndexMapping mapping = null;
    if (mappings != null) {
      for (CustomIndexMapping candidate : mappings) {
        if (candidate != null && candidate.getJsonPath() != null
            && CustomIndexMappingCompiler.samePath(candidate.getJsonPath(), query.getProperty().getPath())) {
          mapping = candidate;
          break;
        }
      }
    }
    if (mapping == null) return null;
    Member member = CustomIndexMappingCompiler.memberForPath(schema, mapping.getJsonPath());
    if (member == null) return null;
    MemberType type = member.getDataType();
    AnyOp op = query.getOp();
    // Boolean REST predicates encode their value in the operation, not query.value.
    if (MemberType.BOOLEAN.equals(type)) {
      if (AnyOp.IS_TRUE.equals(op)) return new IsTrue(member);
      if (AnyOp.IS_FALSE.equals(op)) return new IsFalse(member);
      return null;
    }
    Object value = query.getValue();
    if (value == null) return null;
    if (MemberType.STRING.equals(type) && value instanceof String) {
      if (StringOp.EQUALS.equals(op)) return new Equals(member, value);
      if (StringOp.STARTS_WITH.equals(op)) return new StartsWith(member, (String) value);
      return null;
    }
    if (MemberType.TAG_LIST.equals(type) && AnyOp.CONTAINS.equals(op)) {
      // JSON-looking strings have parsing semantics in PropertyFilter; retain that path.
      return value instanceof String && !looksLikeJson((String) value) ? new TagListContains(member, value) : null;
    }
    if (!(value instanceof Number)) return null;
    double number = ((Number) value).doubleValue();
    if (!Double.isFinite(number)) return null;
    if (MemberType.INT16.equals(type)) return integerComparison(op, member, number, Short.MIN_VALUE, Short.MAX_VALUE);
    if (MemberType.INT32.equals(type)) return integerComparison(op, member, number, Integer.MIN_VALUE, Integer.MAX_VALUE);
    if (MemberType.INT64.equals(type)) {
      return Math.abs(number) < 9_007_199_254_740_992.0
          ? integerComparison(op, member, number, Long.MIN_VALUE, Long.MAX_VALUE) : null;
    }
    if (MemberType.FLOAT32.equals(type)) {
      if ((double) (float) number != number) return null;
      Op translated = comparison(op, member, (float) number);
      return translated == null || DoubleOp.EQ.equals(op) ? translated : new And(translated, new Lte(member, Float.POSITIVE_INFINITY));
    }
    if (MemberType.FLOAT64.equals(type)) {
      Op translated = comparison(op, member, number);
      // SQL orders NaN above infinity; PropertyFilter's Double comparisons reject it.
      return translated == null || DoubleOp.EQ.equals(op) ? translated : new And(translated, new Lte(member, Double.POSITIVE_INFINITY));
    }
    return null;
  }

  private static boolean looksLikeJson(String value) {
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)) {
        return character == '[' || character == '{';
      }
    }
    return false;
  }

  private static @Nullable Op integerComparison(AnyOp op, Member member, double value, long min, long max) {
    if (!List.of(DoubleOp.EQ, DoubleOp.GT, DoubleOp.GTE, DoubleOp.LT, DoubleOp.LTE).contains(op)) return null;
    if (value < (double) min) {
      return DoubleOp.GT.equals(op) || DoubleOp.GTE.equals(op) ? new Not(new IsNull(member)) : never(member);
    }
    if (value > (double) max) {
      return DoubleOp.LT.equals(op) || DoubleOp.LTE.equals(op) ? new Not(new IsNull(member)) : never(member);
    }
    if (DoubleOp.EQ.equals(op)) {
      return Math.floor(value) == value ? new Equals(member, typedInteger(member, (long) value)) : never(member);
    }
    if (DoubleOp.GT.equals(op)) return new Gt(member, typedInteger(member, (long) Math.floor(value)));
    if (DoubleOp.GTE.equals(op)) return new Gte(member, typedInteger(member, (long) Math.ceil(value)));
    if (DoubleOp.LT.equals(op)) return new Lt(member, typedInteger(member, (long) Math.ceil(value)));
    if (DoubleOp.LTE.equals(op)) return new Lte(member, typedInteger(member, (long) Math.floor(value)));
    return null;
  }

  private static Number typedInteger(Member member, long value) {
    if (MemberType.INT16.equals(member.getDataType())) return (short) value;
    if (MemberType.INT32.equals(member.getDataType())) return (int) value;
    return value;
  }

  private static Op never(Member member) {
    Number zero = typedInteger(member, 0);
    return new And(new Gt(member, zero), new Lt(member, zero));
  }

  private static @Nullable Op comparison(AnyOp op, Member member, Number value) {
    if (DoubleOp.EQ.equals(op)) return new Equals(member, value);
    if (DoubleOp.GT.equals(op)) return new Gt(member, value);
    if (DoubleOp.GTE.equals(op)) return new Gte(member, value);
    if (DoubleOp.LT.equals(op)) return new Lt(member, value);
    if (DoubleOp.LTE.equals(op)) return new Lte(member, value);
    return null;
  }
}
