package com.here.naksha.storage.http;

import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.models.storage.POp;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class POpToQueryTest {
  @Test
  void test() {
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
    String queryFromPop = POpToQuery.p0pToQuery(pOp);
    String prettyQueryFromPop = URLDecoder.decode(queryFromPop).replace("&","\n&");

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

  public static String urlEncoded(String text) {
    return URLEncoder.encode(text, UTF_8);
  }
}