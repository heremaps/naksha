package com.here.naksha.storage.http;

import naksha.model.request.query.AnyOp;
import naksha.model.request.query.DoubleOp;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.PAnd;
import naksha.model.request.query.PNot;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IPropertyQueryToQueryConverterTest {

    // Helper to create a standard property path, e.g., "properties.prop_1"
    private static @NotNull Property prop(String propName) {
        return new Property("properties", propName);
    }

    // Helper to create the special top-level ID property
    private static @NotNull Property idProp() {
        return new Property("id");
    }

    @Test
    void andSingle() {
        IPropertyQuery query = new PAnd(
                new PQuery(prop("prop_1"), StringOp.EQUALS, "1")
        );
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=1", result);
    }

    @Test
    void andDiffProp() {
        IPropertyQuery query = new PAnd(
                new PQuery(prop("prop_1"), DoubleOp.EQ, 1),
                new PQuery(prop("prop_2"), DoubleOp.EQ, 2)
        );
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=1&properties.prop_2=2", result);
    }

    @Test
    void andSameProp() {
        IPropertyQuery query = new PAnd(
                new PQuery(prop("prop_1"), DoubleOp.EQ, 1),
                new PQuery(prop("prop_1"), DoubleOp.EQ, 2)
        );
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=1&properties.prop_1=2", result);
    }

    @Test
    void orSingle() {
        IPropertyQuery query = new POr(
                new PQuery(prop("prop_1"), StringOp.EQUALS, "1")
        );
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=1", result);
    }

    @Test
    void orSameProp() {
        IPropertyQuery query = new POr(
                new PQuery(prop("prop_1"), StringOp.EQUALS, "1"),
                new PQuery(prop("prop_1"), StringOp.EQUALS, "2")
        );
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=1,2", result);
    }

    @Test
    void orManyChildren() {
        IPropertyQuery query = new POr(
                new PQuery(prop("prop_1"), DoubleOp.EQ, 1),
                new PQuery(prop("prop_1"), DoubleOp.EQ, 2),
                new PQuery(prop("prop_1"), DoubleOp.EQ, 3),
                new PQuery(prop("prop_1"), DoubleOp.EQ, 4),
                new PQuery(prop("prop_1"), DoubleOp.EQ, 5)
        );
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=1,2,3,4,5", result);
    }

    @Test
    void orDiffProp_throw() {
        IPropertyQuery query = new POr(
                new PQuery(prop("prop_1"), DoubleOp.EQ, 1),
                new PQuery(prop("prop_2"), DoubleOp.EQ, 2)
        );
        assertThrows(IllegalStateException.class, () -> IPropertyQueryToQueryConverter.convert(query));
    }

    @Test
    void orIncompatibleOps_throw() {
        IPropertyQuery query = new POr(
                new PQuery(prop("prop_1"), DoubleOp.EQ, 1),
                new PQuery(prop("prop_1"), DoubleOp.GT, 2)
        );
        assertThrows(IllegalStateException.class, () -> IPropertyQueryToQueryConverter.convert(query));
    }

    @Test
    void nullOrValue() {
        IPropertyQuery query = new POr(
                new PQuery(prop("prop_1"), DoubleOp.EQ, 1),
                new PQuery(prop("prop_1"), AnyOp.IS_NULL, null)
        );
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=1,.null", result);
    }

    @Test
    void equals() {
        IPropertyQuery query = new PQuery(prop("prop_1"), StringOp.EQUALS, "1");
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=1", result);
    }

    @Test
    void notEquals() {
        IPropertyQuery query = new PNot(new PQuery(prop("prop_1"), StringOp.EQUALS, "1"));
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1!=1", result);
    }

    @Test
    void existsSingle() {
        IPropertyQuery query = new PQuery(prop("prop_1"), AnyOp.EXISTS, null);
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1!=.null", result);
    }

    @Test
    void notExistsSingle() {
        IPropertyQuery query = new PNot(new PQuery(prop("prop_1"), AnyOp.EXISTS, null));
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=.null", result);
    }

    @Test
    void containsJson() throws UnsupportedEncodingException {
        String json = "{\"num\":1,\"str\":\"str1\"}";
        IPropertyQuery query = new PQuery(prop("prop_1"), AnyOp.CONTAINS, json);
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("properties.prop_1=cs=" + urlEncoded(json), result);
    }

    @Test
    void simpleAndOperations() {
        IPropertyQuery query = new PAnd(
                new PQuery(prop("prop_1"), DoubleOp.EQ, 1),
                new PQuery(prop("prop_2"), DoubleOp.GT, 2),
                new PQuery(prop("prop_3"), DoubleOp.GTE, 3),
                new PQuery(prop("prop_4"), DoubleOp.LT, 4),
                new PQuery(prop("prop_5"), DoubleOp.LTE, 5)
        );
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals(
                "properties.prop_1=1"
                        + "&properties.prop_2=gt=2"
                        + "&properties.prop_3=gte=3"
                        + "&properties.prop_4=lt=4"
                        + "&properties.prop_5=lte=5",
                result
        );
    }

    @Test
    void addPrefixToIdProp() {
        IPropertyQuery query = new PQuery(idProp(), DoubleOp.EQ, 1);
        String result = IPropertyQueryToQueryConverter.convert(query);
        assertEquals("f.id=1", result);
    }

    @ParameterizedTest
    @MethodSource("getOpsIncompatibleWithNot")
    void notWithIncompatibleOperation_throw(IPropertyQuery incompatibleOp) {
        IPropertyQuery query = new PNot(incompatibleOp);
        assertThrows(UnsupportedOperationException.class, () -> IPropertyQueryToQueryConverter.convert(query));
    }

    // Provides a stream of operations that cannot be logically inverted by the converter.
    public static Stream<IPropertyQuery> getOpsIncompatibleWithNot() {
        return Stream.of(
                new PQuery(prop("prop_1"), DoubleOp.GT, 1),
                new PQuery(prop("prop_1"), DoubleOp.GTE, 1),
                new PQuery(prop("prop_1"), DoubleOp.LT, 1),
                new PQuery(prop("prop_1"), DoubleOp.LTE, 1),
                new PQuery(prop("prop_1"), AnyOp.CONTAINS, "{}")
        );
    }

    private static String urlEncoded(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}