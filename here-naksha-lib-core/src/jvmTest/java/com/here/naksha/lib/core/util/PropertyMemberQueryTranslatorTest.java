package com.here.naksha.lib.core.util;

import static org.junit.jupiter.api.Assertions.*;

import naksha.model.objects.CustomIndexMapping;
import naksha.model.objects.CustomIndexMappingList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import naksha.model.objects.*;
import naksha.model.request.*;
import naksha.model.request.ops.*;
import naksha.model.request.query.*;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import org.junit.jupiter.api.Test;

class PropertyMemberQueryTranslatorTest {
  private NakshaCollection schema() {
    return schema(MemberType.INT32);
  }

  private NakshaCollection schema(MemberType type) {
    NakshaCollection schema = new NakshaCollection("mapped", "catalog");
    schema.setCustomIndexMapping(new CustomIndexMappingList(new CustomIndexMapping(new JsonPath("properties", "value"), type)));
    return CustomIndexMappingCompiler.expand(schema);
  }

  /** Translates the expression through the public API, returns the member query or null for the legacy path. */
  private Op translated(IPropertyQuery expression, NakshaCollection schema) {
    ReadFeatures adapted = PropertyMemberQueryTranslator.adapt(new ReadFeatures().withPropertyQuery(expression), schema, schema);
    return adapted == null ? null : adapted.getQueryMembers();
  }

  private PQuery query(AnyOp op) {
    return query(op, null);
  }

  private PQuery query(AnyOp op, Object value) {
    return new PQuery(new Property("properties", "value"), op, value);
  }

  /** A query of the key "city" directly below the mapped path. */
  private PQuery key(AnyOp op, Object value) {
    return new PQuery(new Property("properties", "value", "city"), op, value);
  }

  /** Asserts the value and its runtime type, because the storage checks the operand type. */
  private void assertOperand(Object expected, Object actual) {
    assertEquals(expected, actual);
    assertEquals(expected.getClass(), actual.getClass());
  }

  @Test void translatesBooleanOperationsAndBooleanContainment() {
    NakshaCollection schema = schema(MemberType.BOOLEAN);
    assertEquals("cm01", assertInstanceOf(IsTrue.class, translated(query(AnyOp.IS_TRUE), schema)).getAt());
    assertEquals("cm01", assertInstanceOf(IsFalse.class, translated(query(AnyOp.IS_FALSE), schema)).getAt());
    assertInstanceOf(IsTrue.class, translated(query(AnyOp.CONTAINS, true), schema));
    assertInstanceOf(IsFalse.class, translated(query(AnyOp.CONTAINS, false), schema));
    assertNull(translated(query(StringOp.CONTAINS, "true"), schema));
    assertNull(translated(new PAnd(query(AnyOp.IS_TRUE), query(StringOp.EQUALS, "true")), schema));
  }

  @Test void passesNumericOperandsUnchanged() {
    // The storage decides if an operand fits the member, for example it rejects a Long or a Double for INT32.
    Gt gt = assertInstanceOf(Gt.class, translated(query(DoubleOp.GT, 50.5), schema()));
    assertEquals("cm01", gt.getAt());
    assertOperand(50.5, gt.getValue());
    assertOperand(50.5, assertInstanceOf(Lt.class, translated(query(DoubleOp.LT, 50.5), schema())).getValue());
    assertOperand(50L, assertInstanceOf(Equals.class, translated(query(DoubleOp.EQ, 50L), schema())).getValue());
    assertOperand(51L, assertInstanceOf(Equals.class, translated(query(DoubleOp.CONTAINS, 51L), schema())).getValue());
    assertOperand(2147483648L, assertInstanceOf(Equals.class, translated(query(DoubleOp.EQ, 2147483648L), schema())).getValue());
    assertOperand(7L, assertInstanceOf(Gte.class, translated(query(DoubleOp.GTE, 7L), schema(MemberType.INT16))).getValue());
    assertOperand(9_007_199_254_740_993L,
        assertInstanceOf(Equals.class, translated(query(DoubleOp.EQ, 9_007_199_254_740_993L), schema(MemberType.INT64))).getValue());
    assertOperand(0.1, assertInstanceOf(Equals.class, translated(query(DoubleOp.EQ, 0.1), schema(MemberType.FLOAT32))).getValue());
    assertOperand(0.5f, assertInstanceOf(Lte.class, translated(query(DoubleOp.LTE, 0.5f), schema(MemberType.FLOAT64))).getValue());
    assertOperand(0L, assertInstanceOf(Equals.class, translated(query(DoubleOp.EQ, 0L), schema(MemberType.FLOAT64))).getValue());
  }

  @Test void passesInt8OperandsUnchanged() {
    // The mapping validation rejects INT8, therefore the member is declared directly.
    JsonPath path = new JsonPath("properties", "value");
    NakshaCollection schema = new NakshaCollection("mapped", "catalog");
    schema.setCustomIndexMapping(new CustomIndexMappingList(new CustomIndexMapping(path, MemberType.INT8)));
    schema.addMember(new Member("cm01", MemberType.INT8, path));
    Equals equals = assertInstanceOf(Equals.class, translated(query(DoubleOp.EQ, 7L), schema));
    assertEquals("cm01", equals.getAt());
    assertOperand(7L, equals.getValue());
    assertOperand(50.5, assertInstanceOf(Gt.class, translated(query(DoubleOp.GT, 50.5), schema)).getValue());
  }

  @Test void translatesStringsAndLiteralTagListContainment() {
    NakshaCollection strings = schema(MemberType.STRING);
    assertEquals("abc", assertInstanceOf(Equals.class, translated(query(StringOp.EQUALS, "abc"), strings)).getValue());
    assertEquals("abc", assertInstanceOf(Equals.class, translated(query(StringOp.CONTAINS, "abc"), strings)).getValue());
    assertEquals("a_%", assertInstanceOf(StartsWith.class, translated(query(StringOp.STARTS_WITH, "a_%"), strings)).getValue());
    for (String item : new String[] {"red", " red ", "{\"a\":1}", "[red", "red]", " [red "}) {
      TagListContains contains = assertInstanceOf(TagListContains.class,
          translated(query(StringOp.CONTAINS, item), schema(MemberType.TAG_LIST)));
      assertEquals(item, contains.getItem());
    }
  }

  @Test void translatesTagListArraysIntoContainsAllOf() {
    NakshaCollection schema = schema(MemberType.TAG_LIST);
    TagListContainsAllOf both = assertInstanceOf(TagListContainsAllOf.class,
        translated(query(StringOp.CONTAINS, "[\"red\",\"blue\"]"), schema));
    assertEquals("cm01", both.getAt());
    assertEquals(List.of("red", "blue"), new ArrayList<>(both.getItems()));
    TagListContainsAllOf padded = assertInstanceOf(TagListContainsAllOf.class,
        translated(query(StringOp.CONTAINS, " [\"red\",\"blue\"] "), schema));
    assertEquals(List.of("red", "blue"), new ArrayList<>(padded.getItems()));
    TagListContainsAllOf empty = assertInstanceOf(TagListContainsAllOf.class, translated(query(StringOp.CONTAINS, "[]"), schema));
    assertTrue(empty.getItems().isEmpty());
    // A one-element array searches a JSON-looking tag literally.
    TagListContainsAllOf literal = assertInstanceOf(TagListContainsAllOf.class,
        translated(query(StringOp.CONTAINS, "[\"[\\\"red\\\"]\"]"), schema));
    assertEquals(List.of("[\"red\"]"), new ArrayList<>(literal.getItems()));
    // Only arrays of strings are tag lists, everything else stays on the legacy path, also as part of an expression.
    for (String array : new String[] {"[1]", "[null]", "[\"a\",1]", "[[\"a\"]]", "[{\"a\":\"b\"}]", "[\"a\",]", "[red]"}) {
      assertNull(translated(query(StringOp.CONTAINS, array), schema), array);
    }
    assertNull(translated(new PAnd(query(StringOp.CONTAINS, "red"), query(StringOp.CONTAINS, "[1]")), schema));
    Or or = assertInstanceOf(Or.class, translated(new POr(query(StringOp.CONTAINS, "red"), query(StringOp.CONTAINS, "[\"a\"]")), schema));
    assertInstanceOf(TagListContains.class, or.getChildren().get(0));
    assertInstanceOf(TagListContainsAllOf.class, or.getChildren().get(1));
  }

  @Test void translatesDirectTagMapKeys() {
    NakshaCollection schema = schema(MemberType.TAG_MAP);
    TagEquals string = assertInstanceOf(TagEquals.class, translated(key(StringOp.EQUALS, "Berlin"), schema));
    assertEquals("cm01", string.getAt());
    assertEquals("city", string.getKey());
    assertOperand("Berlin", string.getValue());
    assertOperand(20L, assertInstanceOf(TagEquals.class, translated(key(DoubleOp.EQ, 20L), schema)).getValue());
    assertOperand(4.5, assertInstanceOf(TagEquals.class, translated(key(DoubleOp.EQ, 4.5), schema)).getValue());
    assertOperand(20L, assertInstanceOf(TagGt.class, translated(key(DoubleOp.GT, 20L), schema)).getValue());
    assertOperand(4.5, assertInstanceOf(TagGte.class, translated(key(DoubleOp.GTE, 4.5), schema)).getValue());
    assertOperand(20L, assertInstanceOf(TagLt.class, translated(key(DoubleOp.LT, 20L), schema)).getValue());
    TagLte lte = assertInstanceOf(TagLte.class, translated(key(DoubleOp.LTE, 4.5), schema));
    assertEquals("city", lte.getKey());
    assertOperand(4.5, lte.getValue());
    assertOperand(true, assertInstanceOf(TagEquals.class, translated(key(AnyOp.IS_TRUE, null), schema)).getValue());
    assertOperand(false, assertInstanceOf(TagEquals.class, translated(key(AnyOp.IS_FALSE, null), schema)).getValue());
    // Primitive containment is native equality.
    assertOperand("Berlin", assertInstanceOf(TagEquals.class, translated(key(StringOp.CONTAINS, "Berlin"), schema)).getValue());
    assertOperand(20L, assertInstanceOf(TagEquals.class, translated(key(DoubleOp.CONTAINS, 20L), schema)).getValue());
    assertOperand(true, assertInstanceOf(TagEquals.class, translated(key(AnyOp.CONTAINS, true), schema)).getValue());
    TagStartsWith startsWith = assertInstanceOf(TagStartsWith.class, translated(key(StringOp.STARTS_WITH, "Ber"), schema));
    assertEquals("city", startsWith.getKey());
    assertEquals("Ber", startsWith.getValue());
    Not negated = assertInstanceOf(Not.class, translated(new PNot(key(StringOp.EQUALS, "Berlin")), schema));
    assertInstanceOf(TagEquals.class, negated.getChild());
    // Operations without a native tag counterpart.
    assertNull(translated(key(StringOp.EQUALS, null), schema));
    assertNull(translated(key(DoubleOp.GT, "20"), schema));
    assertNull(translated(key(AnyOp.IS_ANY_OF, "Berlin"), schema));
    assertNull(translated(key(StringOp.NOT_EQUALS, "Berlin"), schema));
    assertNull(translated(new PAnd(key(StringOp.EQUALS, "Berlin"), key(DoubleOp.GT, "20")), schema));
  }

  @Test void distinguishesExplicitNullAndMissingTagMapKeys() {
    NakshaCollection schema = schema(MemberType.TAG_MAP);
    assertEquals("city", assertInstanceOf(TagMapHasKey.class, translated(key(AnyOp.EXISTS, null), schema)).getKey());
    assertEquals("city", assertInstanceOf(TagIsNull.class, translated(key(AnyOp.IS_NULL, null), schema)).getKey());
    Not notNull = assertInstanceOf(Not.class, translated(key(AnyOp.IS_NOT_NULL, null), schema));
    assertEquals("city", assertInstanceOf(TagIsNull.class, notNull.getChild()).getKey());
    // REST parses "p.value.city=.null" into a negated existence check.
    Not missing = assertInstanceOf(Not.class, translated(new PNot(key(AnyOp.EXISTS, null)), schema));
    assertEquals("city", assertInstanceOf(TagMapHasKey.class, missing.getChild()).getKey());
    // The whole map keeps the generic member checks.
    assertInstanceOf(IsNull.class, translated(query(AnyOp.IS_NULL), schema));
    assertInstanceOf(IsNull.class, assertInstanceOf(Not.class, translated(query(AnyOp.EXISTS), schema)).getChild());
  }

  @Test void resolvesOnlyDirectTagMapKeysByTypedSegments() {
    NakshaCollection schema = schema(MemberType.TAG_MAP);
    assertNull(translated(new PQuery(new Property("properties", "value", "address", "city"), StringOp.EQUALS, "Berlin"), schema));
    assertNull(translated(new PQuery(new Property("properties", "value", "address", "city"), AnyOp.EXISTS), schema));
    assertNull(translated(new PQuery(new Property("properties", "value.city"), StringOp.EQUALS, "Berlin"), schema));
    assertNull(translated(new PQuery(new Property("properties", "valueX", "city"), StringOp.EQUALS, "Berlin"), schema));
    assertNull(translated(new PQuery(new Property("properties"), AnyOp.EXISTS), schema));
    // Keys exist only below tag maps.
    assertNull(translated(key(StringOp.EQUALS, "Berlin"), schema(MemberType.STRING)));
    assertNull(translated(key(AnyOp.EXISTS, null), schema(MemberType.TAG_LIST)));
    // The whole map can't be compared to a primitive.
    assertNull(translated(query(StringOp.EQUALS, "Berlin"), schema));

    // An array index of the mapped path is not the same as a string key.
    NakshaCollection indexed = new NakshaCollection("mapped", "catalog");
    indexed.setCustomIndexMapping(new CustomIndexMappingList(
        new CustomIndexMapping(new JsonPath("properties", "items", 0), MemberType.TAG_MAP)));
    indexed = CustomIndexMappingCompiler.expand(indexed);
    assertNull(translated(new PQuery(new Property("properties", "items", "0", "city"), StringOp.EQUALS, "Berlin"), indexed));
    Property typed = new Property("properties", "items");
    typed.getPath().add(0);
    typed.getPath().add("city");
    assertEquals("city", assertInstanceOf(TagEquals.class, translated(new PQuery(typed, StringOp.EQUALS, "Berlin"), indexed)).getKey());
  }

  @Test void translatesFlatTagMapContainment() {
    NakshaCollection schema = schema(MemberType.TAG_MAP);
    And and = assertInstanceOf(And.class,
        translated(query(StringOp.CONTAINS, "{\"city\":\"Berlin\",\"zip\":10115,\"rating\":4.5,\"open\":true,\"note\":null}"), schema));
    assertEquals(5, and.getChildren().size());
    List<String> keys = new ArrayList<>();
    List<Object> values = new ArrayList<>();
    for (Op child : and.getChildren()) {
      TagEquals equals = assertInstanceOf(TagEquals.class, child);
      assertEquals("cm01", equals.getAt());
      keys.add(equals.getKey());
      values.add(equals.getValue());
    }
    assertEquals(List.of("city", "zip", "rating", "open", "note"), keys);
    assertEquals(Arrays.asList("Berlin", 10115, 4.5, true, null), values);
    TagEquals single = assertInstanceOf(TagEquals.class, translated(query(StringOp.CONTAINS, "{\"city\":\"Berlin\"}"), schema));
    assertEquals("city", single.getKey());
    assertEquals("Berlin", single.getValue());
    Not empty = assertInstanceOf(Not.class, translated(query(StringOp.CONTAINS, "{}"), schema));
    assertEquals("cm01", assertInstanceOf(IsNull.class, empty.getChild()).getAt());
  }

  @Test void unsupportedTagMapContainmentKeepsTheWholeExpression() {
    NakshaCollection schema = schema(MemberType.TAG_MAP);
    for (String object : new String[] {
        "{\"address\":{\"city\":\"Berlin\"}}", "{\"city\":\"Berlin\",\"tags\":[\"a\"]}", "{\"city\":",
        "{city}", "[{\"city\":\"Berlin\"}]", "null"}) {
      assertNull(translated(query(StringOp.CONTAINS, object), schema), object);
      assertNull(translated(new POr(query(StringOp.CONTAINS, object), query(StringOp.CONTAINS, "[" + object + "]")), schema), object);
      assertNull(translated(new PAnd(key(StringOp.EQUALS, "Berlin"), query(StringOp.CONTAINS, object)), schema), object);
    }
    assertNull(translated(query(DoubleOp.CONTAINS, 1L), schema));
  }

  @Test void recognizesOnlyTheExactObjectContainmentPair() {
    NakshaCollection schema = schema(MemberType.TAG_MAP);
    String object = "{\"city\":\"Berlin\"}";
    TagEquals pair = assertInstanceOf(TagEquals.class,
        translated(new POr(query(StringOp.CONTAINS, object), query(StringOp.CONTAINS, "[" + object + "]")), schema));
    assertEquals("city", pair.getKey());
    assertEquals("Berlin", pair.getValue());
    assertInstanceOf(Not.class,
        translated(new POr(query(StringOp.CONTAINS, "{}"), query(StringOp.CONTAINS, "[{}]")), schema));

    // Near misses are translated as normal OR, where the array branch has no native counterpart.
    assertNull(translated(new POr(query(StringOp.CONTAINS, object), query(StringOp.CONTAINS, "[{\"city\":\"Hamburg\"}]")), schema));
    assertNull(translated(new POr(query(StringOp.CONTAINS, "[" + object + "]"), query(StringOp.CONTAINS, object)), schema));
    assertNull(translated(new POr(query(StringOp.CONTAINS, object), query(StringOp.EQUALS, "[" + object + "]")), schema));
    assertNull(translated(new POr(query(StringOp.CONTAINS, object),
        new PQuery(new Property("properties", "other"), StringOp.CONTAINS, "[" + object + "]")), schema));
    assertNull(translated(new POr(query(StringOp.CONTAINS, object), query(StringOp.CONTAINS, "[" + object + "]"),
        query(AnyOp.IS_NULL)), schema));
    // A mixed OR keeps all of its branches.
    Or mixed = assertInstanceOf(Or.class, translated(new POr(query(StringOp.CONTAINS, object), query(AnyOp.IS_NULL)), schema));
    assertInstanceOf(TagEquals.class, mixed.getChildren().get(0));
    assertInstanceOf(IsNull.class, mixed.getChildren().get(1));

    // The pair is only special for tag maps; on a key below the map, both branches are primitive equality.
    Or keyPair = assertInstanceOf(Or.class,
        translated(new POr(key(StringOp.CONTAINS, object), key(StringOp.CONTAINS, "[" + object + "]")), schema));
    assertEquals(object, assertInstanceOf(TagEquals.class, keyPair.getChildren().get(0)).getValue());
    assertEquals("[" + object + "]", assertInstanceOf(TagEquals.class, keyPair.getChildren().get(1)).getValue());
    Or strings = assertInstanceOf(Or.class, translated(
        new POr(query(StringOp.CONTAINS, object), query(StringOp.CONTAINS, "[" + object + "]")), schema(MemberType.STRING)));
    assertEquals(2, strings.getChildren().size());
    assertNull(translated(new POr(query(StringOp.CONTAINS, object), query(StringOp.CONTAINS, "[" + object + "]")),
        schema(MemberType.TAG_LIST)));
  }

  @Test void leavesTheOriginalExpressionUnchanged() {
    NakshaCollection schema = schema(MemberType.TAG_MAP);
    String object = "{\"city\":\"Berlin\",\"zip\":10115}";
    POr pair = new POr(query(StringOp.CONTAINS, object), query(StringOp.CONTAINS, "[" + object + "]"));
    PAnd expression = new PAnd(pair, key(DoubleOp.GT, 20L));
    ReadFeatures original = new ReadFeatures().withPropertyQuery(expression);
    ReadFeatures adapted = PropertyMemberQueryTranslator.adapt(original, schema, schema);
    assertNotNull(adapted);
    assertInstanceOf(And.class, adapted.getQueryMembers());
    assertNull(adapted.getQuery().getProperties());
    assertSame(expression, original.getQuery().getProperties());
    assertNull(original.getQueryMembers());
    assertEquals(1, original.getResultFilters().size());
    assertEquals(2, expression.size());
    assertEquals(2, pair.size());
    assertEquals(object, ((PQuery) pair.get(0)).getValue());
    assertEquals("[" + object + "]", ((PQuery) pair.get(1)).getValue());
    assertOperand(20L, ((PQuery) expression.get(1)).getValue());
  }

  @Test void unsupportedLeafPreventsPartialTranslation() {
    PQuery a = query(DoubleOp.EQ, 50);
    PQuery b = new PQuery(new Property("unmapped"), StringOp.EQUALS, "x");
    assertNull(translated(new PAnd(a, b), schema()));
    assertNull(translated(new POr(a, b), schema()));
    assertNull(translated(new PNot(b), schema()));
    assertNull(translated(new PNot(new PAnd(a, b)), schema()));
    assertNull(translated(new PAnd(a, new PNot(b)), schema()));
    assertNull(translated(new POr(a, new PNot(b)), schema()));
    for (AnyOp op : new AnyOp[] {AnyOp.EXISTS, AnyOp.IS_NULL, AnyOp.IS_NOT_NULL}) {
      assertNull(translated(new PQuery(new Property("unmapped"), op), schema()));
    }
    // Mapped paths with an operation or value that has no native counterpart.
    assertNull(translated(query(StringOp.EQUALS, "50"), schema()));
    assertNull(translated(new POr(a, query(StringOp.EQUALS, "50")), schema()));
    assertNull(translated(query(AnyOp.IS_TRUE), schema()));
    assertNull(translated(query(DoubleOp.EQ, 50), schema(MemberType.STRING)));
    assertNull(translated(query(StringOp.CONTAINS, "{\"color\":{\"name\":\"red\"}}"), schema(MemberType.TAG_MAP)));
    assertNull(translated(query(DoubleOp.NE, 50L), schema()));
    assertNull(translated(query(StringOp.NOT_EQUALS, "abc"), schema(MemberType.STRING)));
    assertNull(translated(query(DoubleOp.CONTAINS, 1L), schema(MemberType.TAG_LIST)));
    assertInstanceOf(Or.class, translated(new POr(a, query(DoubleOp.GT, 60)), schema()));
  }

  @Test void translatesNativeNullChecksWithoutQueryValues() {
    for (MemberType type : new MemberType[] {
        MemberType.BOOLEAN, MemberType.INT32, MemberType.STRING, MemberType.TAG_LIST, MemberType.TAG_MAP}) {
      NakshaCollection schema = schema(type);
      assertEquals("cm01", assertInstanceOf(IsNull.class, translated(query(AnyOp.IS_NULL), schema)).getAt());
      for (AnyOp op : new AnyOp[] {AnyOp.EXISTS, AnyOp.IS_NOT_NULL}) {
        Not not = assertInstanceOf(Not.class, translated(query(op), schema));
        assertEquals("cm01", assertInstanceOf(IsNull.class, not.getChild()).getAt());
      }
    }
  }

  @Test void translatesNegatedExpressions() {
    Not numeric = assertInstanceOf(Not.class, translated(new PNot(query(DoubleOp.EQ, 50)), schema()));
    Equals numericChild = assertInstanceOf(Equals.class, numeric.getChild());
    assertEquals("cm01", numericChild.getAt());
    assertEquals(50, numericChild.getValue());

    Not string = assertInstanceOf(Not.class,
        translated(new PNot(query(StringOp.EQUALS, "abc")), schema(MemberType.STRING)));
    assertEquals("abc", assertInstanceOf(Equals.class, string.getChild()).getValue());

    PQuery a = query(DoubleOp.EQ, 50);
    PQuery b = query(DoubleOp.GT, 60);
    Not and = assertInstanceOf(Not.class, translated(new PNot(new PAnd(a, new PNot(b))), schema()));
    assertInstanceOf(And.class, and.getChild());
    Not or = assertInstanceOf(Not.class, translated(new PNot(new POr(a, b)), schema()));
    assertInstanceOf(Or.class, or.getChild());
    Not outer = assertInstanceOf(Not.class, translated(new PNot(new PNot(a)), schema()));
    assertInstanceOf(Equals.class, assertInstanceOf(Not.class, outer.getChild()).getChild());
    Not isNull = assertInstanceOf(Not.class, translated(new PNot(query(AnyOp.IS_NULL)), schema()));
    assertInstanceOf(IsNull.class, isNull.getChild());
  }

  @Test void replacesOnlyTheLegacyPropertyFilterOfACopy() {
    ResultFilter unrelated = tuple -> tuple;
    PropertyFilter foreign = new PropertyFilter(new ReadFeatures().withPropertyQuery(query(DoubleOp.EQ, 99)));
    PQuery expression = query(DoubleOp.EQ, 50);
    ReadFeatures original = new ReadFeatures().withCatalogId("catalog").withCollectionId("mapped").withPropertyQuery(expression);
    original.getResultFilters().add(unrelated);
    original.getResultFilters().add(foreign);
    original.setLimit(1);
    original.getFeatureIds().add("one");
    List<ResultFilter> filters = new ArrayList<>(original.getResultFilters());
    assertEquals(3, filters.size());

    ReadFeatures adapted = PropertyMemberQueryTranslator.adapt(original, schema(), schema());
    assertNotNull(adapted);
    assertNull(original.getQueryMembers());
    assertSame(expression, original.getQuery().getProperties());
    assertEquals(List.of("one"), new ArrayList<>(original.getFeatureIds()));
    assertEquals(filters, new ArrayList<>(original.getResultFilters()));

    assertEquals(List.of(unrelated, foreign), new ArrayList<>(adapted.getResultFilters()));
    assertEquals(1, adapted.getLimit());
    assertTrue(adapted.getFeatureIds().isEmpty());
    assertNull(adapted.getQuery().getProperties());
    assertInstanceOf(And.class, adapted.getQueryMembers());
  }

  @Test void keepsHistoryAndMemberQueriesOnLegacyPath() {
    ReadFeatures request = new ReadFeatures().withPropertyQuery(query(DoubleOp.EQ, 50));
    request.setQueryHistory(true);
    assertNull(PropertyMemberQueryTranslator.adapt(request, schema(), schema()));
    request.setQueryHistory(false);
    request.setVersions(2);
    assertNull(PropertyMemberQueryTranslator.adapt(request, schema(), schema()));
    request.setVersions(1);
    Equals nativeQuery = new Equals(StandardMembers.Id, "one");
    request.setQueryMembers(nativeQuery);
    assertNull(PropertyMemberQueryTranslator.adapt(request, schema(), schema()));
    assertSame(nativeQuery, request.getQueryMembers());
  }

  @Test void translatesBboxAndTileRequests() {
    ReadFeatures request = new ReadFeaturesProxyWrapper().withPropertyQuery(query(DoubleOp.EQ, 50));
    ReadFeatures adapted = PropertyMemberQueryTranslator.adapt(request, schema(), schema());
    assertInstanceOf(ReadFeaturesProxyWrapper.class, adapted);
    assertNotNull(adapted.getQueryMembers());
    assertTrue(adapted.getResultFilters().isEmpty());
    assertNotNull(request.getQuery().getProperties());
    assertEquals(1, request.getResultFilters().size());

    // A wrapper around a request recognizes the property filter of the wrapped request.
    ReadFeatures wrapped = ReadFeaturesProxyWrapper.proxyWrapperOf(new ReadFeatures().withPropertyQuery(query(DoubleOp.EQ, 50)));
    ReadFeatures adaptedWrapper = PropertyMemberQueryTranslator.adapt(wrapped, schema(), schema());
    assertNotNull(adaptedWrapper);
    assertTrue(adaptedWrapper.getResultFilters().isEmpty());
  }
}
