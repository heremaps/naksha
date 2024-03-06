package com.here.naksha.storage.http;

import com.here.naksha.lib.core.lambdas.P;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.PRef;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.here.naksha.storage.http.POpToQuery.POpToQueryConversionException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.stream.Stream;

import static com.here.naksha.lib.core.models.storage.POp.*;
import static com.here.naksha.storage.http.POpToQuery.p0pToQuery;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class POpToQueryTest {
  @Test
  void convertQueryParamList() {
    final QueryParameterList params = new QueryParameterList("id=1"
            + urlEncoded("f.id") + "=" + urlEncoded("@value:1") + ",'12345'"
            + "&p.prop_2!=value_2,value_22"
            + "&p.prop_3=.null,value_33"
            + "&p.prop_4!=.null,value_44"
            + "&p.prop_5>=5.5,55"
            + "&p.prop_5_1@>" + urlEncoded("{\"id\":\"123\"}") + ",.null"
            + "&p.prop_5_2!=" + urlEncoded("{\"id\":\"123\"}") + "," + urlEncoded("{\"id\":\"456\"}") + "," + ".null"
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

    POp pOp = PropertySearchUtil.buildOperationForPropertySearchParams(params);
    String queryFromPop = p0pToQuery(pOp);
    String prettyQueryFromPop = URLDecoder.decode(queryFromPop).replace("&", "\n&");

    String prettyExpectedQuery = """
            properties.prop_2!=value_2,value_22
            &properties.prop_3=.null,value_33
            &properties.prop_4!=.null,value_44
            &properties.prop_5=gte=5.5,55
            &properties.prop_5_1=cs={"id":"123"},[{"id":"123"}],.null
            &properties.prop_5_2!={"id":"123"},{"id":"456"},.null
            &properties.prop_6=lte=6,66
            &properties.prop_7=gt=7,77
            &properties.prop_8=lt=8,88
            &properties.array_1=cs=@element_1,element_2
            &properties.prop_10=gte=555,5555
            &properties.prop_11=lte=666,6666
            &properties.prop_12=gt=777,7777
            &properties.prop_13=lt=888,8888
            &properties.@ns:com:here:xyz.tags=cs={"id":"123"},[{"id":"123"}],element_4
            &properties.@ns:com:here:xyz.tags=cs=element_5""";

    assertEquals(prettyExpectedQuery, prettyQueryFromPop);
  }

  @Test
  void andSingle() {
    POp pOp = and(
            eq(propRef("prop_1"), "1")
    );

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=1");
  }

  @Test
  void andDiffProp() {
    POp pOp = and(
            eq(propRef("prop_1"), "1"),
            eq(propRef("prop_2"), "2")
    );

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=1&property.prop_2=2");
  }

  @Test
  void andSameProp() {
    POp pOp = and(
            eq(propRef("prop_1"), "1"),
            eq(propRef("prop_1"), "2")
    );

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=1&property.prop_1=2");
  }

  @Test
  void andManyChildren() {
    POp pOp = and(
            eq(propRef("prop_1"), "1"),
            eq(propRef("prop_2"), "2"),
            eq(propRef("prop_3"), "3"),
            eq(propRef("prop_4"), "4"),
            eq(propRef("prop_5"), "5")
    );

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=1&property.prop_2=2&property.prop_3=3&property.prop_4=4&property.prop_5=5");
  }

  private static @NotNull PRef propRef(String prop_1) {
    return RequestHelper.pRefFromPropPath(new String[]{"property", prop_1});
  }

  @Test
  void orSingle() {
    POp pOp = or(
            eq(propRef("prop_1"), "1")
    );

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=1");
  }

  @Test
  void orSameProp() {
    POp pOp = or(
            eq(propRef("prop_1"), "1"),
            eq(propRef("prop_1"), "2")
    );

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=1,2");
  }

  @Test
  void orDiffProp_throw() {
    POp pOp = or(
            eq(propRef("prop_1"), "1"),
            eq(propRef("prop_2"), "2")
    );

    assertThrows(POpToQueryConversionException.class, () -> p0pToQuery(pOp));
  }

  @Test
  void orDiffOperator_throw() {
    POp pOp = or(
            eq(propRef("prop_1"), 1),
            gt(propRef("prop_2"), 2)
    );

    assertThrows(POpToQueryConversionException.class, () -> p0pToQuery(pOp));
  }

  @Test
  void orManyChildren() {
    POp pOp = or(
            eq(propRef("prop_1"), "1"),
            eq(propRef("prop_1"), "2"),
            eq(propRef("prop_1"), "3"),
            eq(propRef("prop_1"), "4"),
            eq(propRef("prop_1"), "5")
    );

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=1,2,3,4,5");
  }

  @Test
  void equals() {
    POp pOp = eq(propRef("prop_1"), "1");

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=1");
  }

  @Test
  void notEquals() {
    POp pOp = not(eq(propRef("prop_1"), "1"));

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1!=1");
  }

  @ParameterizedTest
  @MethodSource("notIncompatibleOperations")
  void notWithIncompatibleOperation(POp incompatibleOp) {

    POp pOp = not(incompatibleOp);

    assertThrows(POpToQueryConversionException.class, () -> p0pToQuery(pOp));
  }

  public static POp[] notIncompatibleOperations() {
    return new POp[] {
            or(gt(propRef("prop_1"), 1)),
            and(eq(propRef("prop_1"), "1")),
            gt(propRef("prop_1"), 1),
            gte(propRef("prop_1"), 1),
            lt(propRef("prop_1"), 1),
            lte(propRef("prop_1"), 1),
            contains(propRef("prop_1"), "{}")
    };
  }

  @Test
  void existsSingle() {
    POp pOp = exists(propRef("prop_1"));

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1!=.null");
  }

  @Test
  void notExistsSingle() {
    POp pOp = not(exists(propRef("prop_1")));

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=.null");
  }

  @Test
  void containsJson() {
    String json = "{\"num\":1,\"str\":\"str1\",\"arr\":[1,2,3],\"obj\":{}}";
    POp pOp = contains(propRef("prop_1"), json);

    String query = p0pToQuery(pOp);

    assertEquals(query, "property.prop_1=cs=" + urlEncoded(json));
  }

  @Test
  void simpleLeafOperations() {
    POp pOp = and(
            eq(propRef("prop_1"), 1),
            gt(propRef("prop_2"), 2),
            gte(propRef("prop_3"), 3),
            lt(propRef("prop_4"), 4),
            lte(propRef("prop_5"), 5)
    );

    String query = p0pToQuery(pOp);

    assertEquals(query,
            "property.prop_1=1" +
            "&property.prop_2=gt=2" +
            "&property.prop_3=gte=3" +
            "&property.prop_4=lt=4" +
            "&property.prop_5=lte=5");
  }

  @Test
  void test(){

  }



  public static String urlEncoded(String text) {
    return URLEncoder.encode(text, UTF_8);
  }
}