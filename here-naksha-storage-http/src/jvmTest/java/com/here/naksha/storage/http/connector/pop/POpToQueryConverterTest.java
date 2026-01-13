package com.here.naksha.storage.http.connector.pop;

import com.here.naksha.lib.core.models.payload.events.PropertyQueryAnd;
import com.here.naksha.lib.core.models.payload.events.PropertyQueryOr;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import naksha.model.request.query.AnyOp;

import static com.here.naksha.storage.http.connector.pop.IPropertyQueryToPropertiesQuery.toPoPQueryAnd;
import static com.here.naksha.storage.http.connector.pop.IPropertyQueryToPropertiesQuery.toPopQueryOr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

public class POpToQueryConverterTest {


    @Test
    void andSingle() {
        IPropertyQuery q = and(
                eq(prop("prop_1"), "1")
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":["1"]}
        ]""", query);
    }

    @Test
    void andDiffProp() {
        IPropertyQuery q = and(
                eq(prop("prop_1"), "1"),
                eq(prop("prop_2"), 2)
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":["1"]},
        {"key":"property.prop_2","operation":"EQUALS","values":[2]}
        ]""", query);
    }

    @Test
    void andSameProp() {
        IPropertyQuery q = and(
                eq(prop("prop_1"), "1"),
                eq(prop("prop_1"), "2")
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":["1"]},
        {"key":"property.prop_1","operation":"EQUALS","values":["2"]}
        ]""", query);
    }

    @Test
    void andManyChildren() {
        IPropertyQuery q = and(
                eq(prop("prop_1"), "1"),
                eq(prop("prop_2"), "2"),
                eq(prop("prop_3"), "3"),
                eq(prop("prop_4"), "4"),
                eq(prop("prop_5"), "5")
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":["1"]},
        {"key":"property.prop_2","operation":"EQUALS","values":["2"]},
        {"key":"property.prop_3","operation":"EQUALS","values":["3"]},
        {"key":"property.prop_4","operation":"EQUALS","values":["4"]},
        {"key":"property.prop_5","operation":"EQUALS","values":["5"]}
        ]""", query);
    }


    @Test
    void orSingle() {
        IPropertyQuery q = or(
                eq(prop("prop_1"), "1")
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":["1"]}
        ]""", query);
    }

    @Test
    void orSameProp() {
        IPropertyQuery q = or(
                eq(prop("prop_1"), "1"),
                eq(prop("prop_1"), "2")
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":["1","2"]}
        ]""", query);
    }

    @Test
    void orDiffProp_throw() {
        IPropertyQuery q = or(
                eq(prop("prop_1"), "1"),
                eq(prop("prop_2"), "2")
        );

        assertThrows(
                IPropertyQueryToPropertiesQuery.IPropertyQueryToQueryConversionException.class,
                () -> toPoPQueryAnd(q),
                "Operator OR with two different keys"
        );
    }

    @Test
    void orIncompatibleOps_throw() {
        IPropertyQuery q = or(
                eq(prop("prop_1"), 1),
                gt(prop("prop_2"), 2)
        );

        assertThrows(
                IPropertyQueryToPropertiesQuery.IPropertyQueryToQueryConversionException.class,
                () -> toPoPQueryAnd(q),
                "Operators EQUALS and GREATER_THAN combined in one OR"
        );
    }

    @Test
    void orManyChildren() {
        IPropertyQuery q = or(
                eq(prop("prop_1"), "1"),
                eq(prop("prop_1"), "2"),
                eq(prop("prop_1"), "3"),
                eq(prop("prop_1"), "4"),
                eq(prop("prop_1"), "5")
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":["1","2","3","4","5"]}
        ]""", query);
    }

    @Test
    void nullOrValue() {
        IPropertyQuery q = or(
                eq(prop("prop_1"), 1),
                not(exists(prop("prop_1")))
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":[1,null]}
        ]""", query);
    }

    @Test
    void equals() {
        IPropertyQuery q = eq(prop("prop_1"), "1");

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":["1"]}
        ]""", query);
    }

    @Test
    void notEquals() {
        IPropertyQuery q = not(eq(prop("prop_1"), "1"));

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"NOT_EQUALS","values":["1"]}
        ]""", query);
    }

    @ParameterizedTest
    @MethodSource("getOpsIncompatibleWithNot")
    void notWithIncompatibleOperation_throw(IPropertyQuery incompatibleOp) {
        IPropertyQuery q = not(incompatibleOp);

        assertThrows(
                IPropertyQueryToPropertiesQuery.IPropertyQueryToQueryConversionException.class,
                () -> toPoPQueryAnd(q)
        );
    }

    static IPropertyQuery[] getOpsIncompatibleWithNot() {
        return new IPropertyQuery[]{
                or(gt(prop("prop_1"), 1)),
                and(eq(prop("prop_1"), "1")),
                gt(prop("prop_1"), 1),
                gte(prop("prop_1"), 1),
                lt(prop("prop_1"), 1),
                lte(prop("prop_1"), 1),
                contains(prop("prop_1"), "{}")
        };
    }

    @Test
    void existsSingle() {
        IPropertyQuery q = exists(prop("prop_1"));

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"NOT_EQUALS","values":[null]}
        ]""", query);
    }

    @Test
    void notExistsSingle() {
        IPropertyQuery q = not(exists(prop("prop_1")));

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":[null]}
        ]""", query);
    }

    @Test
    void containsJson() {
        String json = "{\"num\":1,\"str\":\"str1\",\"arr\":[1,2,3],\"obj\":{}}";
        IPropertyQuery q = contains(prop("prop_1"), json);

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"CONTAINS","values":["{\\"num\\":1,\\"str\\":\\"str1\\",\\"arr\\":[1,2,3],\\"obj\\":{}}"]}
        ]""", query);
    }

    @Test
    void simpleLeafOperations() {
        IPropertyQuery q = and(
                eq(prop("prop_1"), 1),
                gt(prop("prop_2"), 2),
                gte(prop("prop_3"), 3),
                lt(prop("prop_4"), 4),
                lte(prop("prop_5"), 5)
        );

        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"property.prop_1","operation":"EQUALS","values":[1]},
        {"key":"property.prop_2","operation":"GREATER_THAN","values":[2]},
        {"key":"property.prop_3","operation":"GREATER_THAN_OR_EQUALS","values":[3]},
        {"key":"property.prop_4","operation":"LESS_THAN","values":[4]},
        {"key":"property.prop_5","operation":"LESS_THAN_OR_EQUALS","values":[5]}
        ]""", query);
    }

    @ParameterizedTest
    @MethodSource("getNotSupportedOps")
    void notSupportedOps_throw(IPropertyQuery notSupportedOp) {
        IPropertyQuery q = not(notSupportedOp);

        assertThrows(
                IPropertyQueryToPropertiesQuery.IPropertyQueryToQueryConversionException.class,
                () -> toPoPQueryAnd(q)
        );
    }

    static IPropertyQuery[] getNotSupportedOps() {
        Property p1 = prop("prop_1");
        return new IPropertyQuery[]{
                startsWith(p1, "1"),
                isNull(p1),
                isNotNull(p1),
        };
    }

    @Test
    void dontAddPrefixToIdProp() {
        IPropertyQuery q = and(
                eq(idProp(), "1")
        );
        PropertyQueryAnd query = toPoPQueryAnd(q);

        assertQueryEquals("""
        [
        {"key":"id","operation":"EQUALS","values":["1"]}
        ]""", query);
    }

    @Test
    void wrapsAndSingleIntoOr() {
        IPropertyQuery q = and(
                eq(prop("prop_1"), "1")
        );

        PropertyQueryOr or = toPopQueryOr(q);

        assertQueryOrEquals("""
                [
                [
                {"key":"property.prop_1","operation":"EQUALS","values":["1"]}
                ]
                ]""", or);
    }

    @Test
    void wrapsAndManyChildrenIntoSingleOrEntry() {
        IPropertyQuery q = and(
                eq(prop("prop_1"), "1"),
                eq(prop("prop_2"), 2)
        );

        PropertyQueryOr or = toPopQueryOr(q);

        assertQueryOrEquals("""
                [
                [
                {"key":"property.prop_1","operation":"EQUALS","values":["1"]},
                {"key":"property.prop_2","operation":"EQUALS","values":[2]}
                ]
                ]""", or);
    }

    @Test
    void wrapsTopLevelOrMergedLeaf() {
        IPropertyQuery q = or(
                eq(prop("prop_1"), "1"),
                eq(prop("prop_1"), "2")
        );

        PropertyQueryOr or = toPopQueryOr(q);

        assertQueryOrEquals("""
                [
                [
                {"key":"property.prop_1","operation":"EQUALS","values":["1","2"]}
                ]
                ]""", or);
    }

    @Test
    void wrapsExistsAndNotExistsThroughNot() {
        IPropertyQuery q = and(
                exists(prop("prop_1")),
                not(exists(prop("prop_2")))
        );

        PropertyQueryOr or = toPopQueryOr(q);

        assertQueryOrEquals("""
                [
                [
                {"key":"property.prop_1","operation":"NOT_EQUALS","values":[null]},
                {"key":"property.prop_2","operation":"EQUALS","values":[null]}
                ]
                ]""", or);
    }

    @Test
    void dontAddPrefixToIdPropInOrWrapper() {
        IPropertyQuery q = and(
                eq(idProp(), "1")
        );

        PropertyQueryOr or = toPopQueryOr(q);

        assertQueryOrEquals("""
                [
                [
                {"key":"id","operation":"EQUALS","values":["1"]}
                ]
                ]""", or);
    }


    private static void assertQueryEquals(String expectedJson, PropertyQueryAnd actualQuery) {
        assertEquals(
                expectedJson.replace(System.lineSeparator(), ""),
                JsonSerializable.serialize(actualQuery)
        );
    }

    private static void assertQueryOrEquals(String expectedJson, PropertyQueryOr actual) {
        assertEquals(
                expectedJson.replace(System.lineSeparator(), ""),
                JsonSerializable.serialize(actual)
        );
    }


    private static @NotNull Property prop(String leaf) {
        return new Property("property", leaf);
    }

    private static @NotNull Property idProp() {
        return new Property("id");
    }

    private static @NotNull IPropertyQuery and(IPropertyQuery... children) { return new PAnd(children); }
    private static @NotNull IPropertyQuery or(IPropertyQuery... children)  { return new POr(children); }
    private static @NotNull IPropertyQuery not(IPropertyQuery q)           { return new PNot(q); }

    private static @NotNull IPropertyQuery exists(Property p)           { return new PQuery(p, AnyOp.EXISTS, null); }
    private static @NotNull IPropertyQuery isNull(Property p)           { return new PQuery(p, AnyOp.IS_NULL, null); }
    private static @NotNull IPropertyQuery isNotNull(Property p)        { return new PQuery(p, AnyOp.IS_NOT_NULL, null); }
    private static @NotNull IPropertyQuery contains(Property p, Object v) { return new PQuery(p, AnyOp.CONTAINS, v); }

    private static @NotNull IPropertyQuery eq(Property p, String v)     { return new PQuery(p, StringOp.EQUALS, v); }
    private static @NotNull IPropertyQuery eq(Property p, Number v)     { return new PQuery(p, DoubleOp.EQ, v); }

    private static @NotNull IPropertyQuery gt(Property p, Number v)     { return new PQuery(p, DoubleOp.GT,  v); }
    private static @NotNull IPropertyQuery gte(Property p, Number v)    { return new PQuery(p, DoubleOp.GTE, v); }
    private static @NotNull IPropertyQuery lt(Property p, Number v)     { return new PQuery(p, DoubleOp.LT,  v); }
    private static @NotNull IPropertyQuery lte(Property p, Number v)    { return new PQuery(p, DoubleOp.LTE, v); }

    private static @NotNull IPropertyQuery startsWith(Property p, String v) {
        return new PQuery(p, StringOp.STARTS_WITH, v);
    }
}
