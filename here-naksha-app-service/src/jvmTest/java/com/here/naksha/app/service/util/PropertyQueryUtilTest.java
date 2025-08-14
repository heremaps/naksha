package com.here.naksha.app.service.util;

import static com.here.naksha.app.common.TestUtil.urlEncoded;
import static com.here.naksha.test.common.assertions.PropertyQueryAssertions.assertThatPropertyQuery;
import static naksha.base.Platform.toInt64;
import static naksha.model.request.RequestQuery.TAGS_PROP_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.here.naksha.app.service.http.ops.PropertyQueryUtil;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import java.util.stream.Stream;
import naksha.base.NakshaException;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.DoubleOp;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.PAnd;
import naksha.model.request.query.POr;
import naksha.model.request.query.StringOp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PropertyQueryUtilTest {

  @Test
  void testBuildOperationForPropertySearchParams() {
    // Given: query params
    final QueryParameterList params = new QueryParameterList(
        "&p.prop_1=value_1"
        + "&p.prop_2!=value_2,value_22"
        + "&p.prop_3=.null,value_33"
        + "&p.prop_4!=.null,value_44"
        + "&p.prop_5>=5.5,55"
        + "&west=-180"
        + "&p.prop_6<=6,66"
        + "&p.prop_7>7,77"
        + "&tags=one,two"
        + "&p.prop_8<8,88"
        + "&p.array_1@>" + urlEncoded("@element_1") + ",element_2"
        + "&p.prop_10=gte=555,5555"
        + "&p.prop_11=lte=666,6666"
        + "&p.prop_12=gt=777,7777"
        + "&p.prop_13=lt=888,8888"
        + "&" + urlEncoded("properties.@ns:com:here:xyz.tags") + "=cs=" + urlEncoded("{\"id\":\"123\"}") + ",element_4"
        + "&f.tags=cs=element_5"
    );

    // When: retrieving query from query params
    final IPropertyQuery retrievedQuery = PropertyQueryUtil.propertyQueryFromParams(params);

    // Then: retrieved query is AND
    final PAnd rootAndQuery = assertQueryIs(PAnd.class, retrievedQuery);

    // And: it contains 15 subclauses
    assertEquals(15, rootAndQuery.size(), "Expected total 15 AND operations");

    // And: first op is simple query
    assertThatPropertyQuery(rootAndQuery.get(0))
        .isPQuery()
        .hasOp(StringOp.EQUALS)
        .hasPropertyWithPath("properties", "prop_1")
        .hasValue("value_1");

    // And: second op is OR
    assertThatPropertyQuery(rootAndQuery.get(1))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPNot()
                .hasChildrenThat(
                    f1 -> f1.isPQuery().hasOp(StringOp.EQUALS).hasPropertyWithPath("properties", "prop_2").hasValue("value_2")
                ),
            second -> second.isPNot()
                .hasChildrenThat(
                    s1 -> s1.isPQuery().hasOp(StringOp.EQUALS).hasPropertyWithPath("properties", "prop_2").hasValue("value_22")
                )
        )
    ;
    // validate operation 3
    assertThatPropertyQuery(rootAndQuery.get(2))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPNot()
                .hasChildrenThat(negated -> negated.isPQuery().hasOp(AnyOp.EXISTS).hasPropertyWithPath("properties", "prop_3")),
            second -> second.isPQuery().hasOp(StringOp.EQUALS).hasPropertyWithPath("properties", "prop_3").hasValue("value_33")
        )
    ;
    // validate operation 4
    assertThatPropertyQuery(rootAndQuery.get(3))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(AnyOp.EXISTS).hasPropertyWithPath("properties", "prop_4"),
            second -> second.isPNot()
                .hasChildrenThat(
                    f1 -> f1.isPQuery().hasOp(StringOp.EQUALS).hasPropertyWithPath("properties", "prop_4").hasValue("value_44")
                )
        )
    ;
    // validate operation 5
    assertThatPropertyQuery(rootAndQuery.get(4))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(DoubleOp.GTE).hasPropertyWithPath("properties", "prop_5").hasValue(5.5),
            second -> second.isPQuery().hasOp(DoubleOp.GTE).hasPropertyWithPath("properties", "prop_5").hasValue(55L)
        )
    ;
    // validate operation 6
    assertThatPropertyQuery(rootAndQuery.get(5))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(DoubleOp.LTE).hasPropertyWithPath("properties", "prop_6").hasValue(6L),
            second -> second.isPQuery().hasOp(DoubleOp.LTE).hasPropertyWithPath("properties", "prop_6").hasValue(66L)
        )
    ;
    // validate operation 7
    assertThatPropertyQuery(rootAndQuery.get(6))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(DoubleOp.GT).hasPropertyWithPath("properties", "prop_7").hasValue(7L),
            second -> second.isPQuery().hasOp(DoubleOp.GT).hasPropertyWithPath("properties", "prop_7").hasValue(77L)
        )
    ;
    // validate operation 8
    assertThatPropertyQuery(rootAndQuery.get(7))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(DoubleOp.LT).hasPropertyWithPath("properties", "prop_8").hasValue(8L),
            second -> second.isPQuery().hasOp(DoubleOp.LT).hasPropertyWithPath("properties", "prop_8").hasValue(88L)
        )
    ;
    // validate operation 9
    assertThatPropertyQuery(rootAndQuery.get(8))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(AnyOp.CONTAINS).hasPropertyWithPath("properties", "array_1").hasValue("@element_1"),
            second -> second.isPQuery().hasOp(AnyOp.CONTAINS).hasPropertyWithPath("properties", "array_1").hasValue("element_2")
        )
    ;
    // validate operation 10
    assertThatPropertyQuery(rootAndQuery.get(9))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(DoubleOp.GTE).hasPropertyWithPath("properties", "prop_10").hasValue(555L),
            second -> second.isPQuery().hasOp(DoubleOp.GTE).hasPropertyWithPath("properties", "prop_10").hasValue(5555L)
        )
    ;
    // validate operation 11
    assertThatPropertyQuery(rootAndQuery.get(10))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(DoubleOp.LTE).hasPropertyWithPath("properties", "prop_11").hasValue(666L),
            second -> second.isPQuery().hasOp(DoubleOp.LTE).hasPropertyWithPath("properties", "prop_11").hasValue(6666L)
        )
    ;
    // validate operation 12
    assertThatPropertyQuery(rootAndQuery.get(11))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(DoubleOp.GT).hasPropertyWithPath("properties", "prop_12").hasValue(777L),
            second -> second.isPQuery().hasOp(DoubleOp.GT).hasPropertyWithPath("properties", "prop_12").hasValue(7777L)
        )
    ;
    // validate operation 13
    assertThatPropertyQuery(rootAndQuery.get(12))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPQuery().hasOp(DoubleOp.LT).hasPropertyWithPath("properties", "prop_13").hasValue(888L),
            second -> second.isPQuery().hasOp(DoubleOp.LT).hasPropertyWithPath("properties", "prop_13").hasValue(8888L)
        )
    ;
    // validate operation 14
    assertThatPropertyQuery(rootAndQuery.get(13))
        .isPOr()
        .hasChildrenThat(
            first -> first.isPOr()
                .hasChildrenThat(
                    f1 -> f1.isPQuery().hasOp(AnyOp.CONTAINS).hasPropertyWithPath(TAGS_PROP_PATH).hasValue("{\"id\":\"123\"}"),
                    f2 -> f2.isPQuery().hasOp(AnyOp.CONTAINS).hasPropertyWithPath(TAGS_PROP_PATH).hasValue("[{\"id\":\"123\"}]")
                ),
            second -> second.hasOp(AnyOp.CONTAINS).hasPropertyWithPath(TAGS_PROP_PATH).hasValue("element_4")
        )
    ;
    // validate operation 15
    assertThatPropertyQuery(rootAndQuery.get(14))
        .isPQuery().hasOp(AnyOp.CONTAINS).hasPropertyWithPath(TAGS_PROP_PATH).hasValue("element_5")
    ;
  }

  @Test
  void shouldIgnoreShortIdQuery() {
    // Given
    final QueryParameterList params = new QueryParameterList(
        urlEncoded("f.id") + "=" + urlEncoded("@value:1") + ",'12345'"
        + "&p.prop_2!=value_2,value_22"
    );

    // When: retrieving query from query params
    final IPropertyQuery retrievedQuery = PropertyQueryUtil.propertyQueryFromParams(params);

    // Then: retrieved query is OR
    final POr rootOrQuery = assertQueryIs(POr.class, retrievedQuery);

    // And: it contains 2 subclauses
    assertEquals(2, rootOrQuery.size(), "Expected single OR operation");

    // And: validate prop_2 subclause
    assertThatPropertyQuery(rootOrQuery)
        .hasChildrenThat(
            first -> first.isPNot()
                .hasChildrenThat(
                    f1 -> f1.isPQuery().hasOp(StringOp.EQUALS).hasPropertyWithPath("properties", "prop_2").hasValue("value_2")
                ),
            second -> second.isPNot()
                .hasChildrenThat(
                    s1 -> s1.isPQuery().hasOp(StringOp.EQUALS).hasPropertyWithPath("properties", "prop_2").hasValue("value_22")
                )
        );
  }

  private static Arguments propQuerySpec(String query, String assertionDesc) {
    return arguments(query, named(assertionDesc, NakshaException.class));
  }

  @ParameterizedTest
  @MethodSource("propQueriesWithException")
  void testKnownException(String queryString, Class<? extends Throwable> exceptionType) {
    assertThrowsExactly(exceptionType, () -> {
      final QueryParameterList queryParameters = new QueryParameterList(queryString);
      PropertyQueryUtil.propertyQueryFromParams(queryParameters);
    });
  }

  private static Stream<Arguments> propQueriesWithException() {
    return Stream.of(
        // invalid delimiter
        propQuerySpec("p.prop_1=1+5", "Exception for invalid delimiter +"),
        // invalid operation on string value
        propQuerySpec("p.prop_1>string_value", "Exception for invalid string operation >"),
        propQuerySpec("p.prop_1<string_value", "Exception for invalid string operation <"),
        propQuerySpec("p.prop_1>=string_value", "Exception for invalid string operation >="),
        propQuerySpec("p.prop_1<=string_value", "Exception for invalid string operation <="),
        propQuerySpec("p.prop_1=gt=string_value", "Exception for invalid string operation =gt="),
        propQuerySpec("p.prop_1=lt=string_value", "Exception for invalid string operation =lt="),
        propQuerySpec("p.prop_1=gte=string_value", "Exception for invalid string operation =gte="),
        propQuerySpec("p.prop_1=lte=string_value", "Exception for invalid string operation =lte="),
        // invalid operation on boolean value
        propQuerySpec("p.prop_1>false", "Exception for invalid boolean operation >"),
        propQuerySpec("p.prop_1<false", "Exception for invalid boolean operation <"),
        propQuerySpec("p.prop_1>=false", "Exception for invalid boolean operation >="),
        propQuerySpec("p.prop_1<=false", "Exception for invalid boolean operation <="),
        propQuerySpec("p.prop_1=gt=false", "Exception for invalid boolean operation =gt="),
        propQuerySpec("p.prop_1=lt=false", "Exception for invalid boolean operation =lt="),
        propQuerySpec("p.prop_1=gte=false", "Exception for invalid boolean operation =gte="),
        propQuerySpec("p.prop_1=lte=false", "Exception for invalid boolean operation =lte=")
    );
  }

  private static <T extends IPropertyQuery> T assertQueryIs(Class<T> type, IPropertyQuery subject) {
    assertInstanceOf(type, subject);
    return type.cast(subject);
  }
}
