package com.here.naksha.lib.handlers.util;

import static com.here.naksha.lib.handlers.util.PropertyOperationUtil.disablePQueriesInRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.lib.core.lambdas.F1;
import java.util.List;
import java.util.Set;
import naksha.base.StringList;
import naksha.model.request.RequestQuery;
import naksha.model.request.query.DoubleOp;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.PAnd;
import naksha.model.request.query.PFalse;
import naksha.model.request.query.PNot;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.PTrue;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;
import org.junit.jupiter.api.Test;

class PropertyOperationUtilTest {

  @Test
  void shouldDisableSingularPQueryInRequest() {
    // Given: request query with POp
    PQuery pQuery = new PQuery(
        new Property("nested", "object"),
        StringOp.EQUALS,
        "text_value"
    );
    RequestQuery query = new RequestQuery();
    query.setProperties(pQuery);

    // When
    Set<PQuery> disabledPQueries = disablePQueriesInRequest(query, pQueryMatchesPath("nested", "object"));

    // Then
    assertEquals(1, disabledPQueries.size());
    assertEquals(pQuery, disabledPQueries.iterator().next());
  }

  @Test
  void shouldComposedPQueryInRequest() {
    // Given: request with dummy query with POp:
    // - query all speed limits of 60 AND "car allowed" signs
    // - "car allowed signs": signs with type `car_allowed` or value that is NOT `can_not_allowed`
    PQuery typeIsSpeedLimit = new PQuery(new Property("sign", "type"), StringOp.EQUALS, "speed_limit");
    PQuery valueIs60 = new PQuery(new Property("sign", "value"), DoubleOp.EQ, 60.0);
    PQuery typeIsCarAllowed = new PQuery(new Property("sign", "type"), StringOp.EQUALS, "car_allowed");
    PQuery valueIsCarNotAllowed = new PQuery(new Property("sign", "value"), StringOp.EQUALS, "car_not_allowed");
    IPropertyQuery originalPropertyQuery = new POr(
        new PAnd(typeIsSpeedLimit, valueIs60),
        new POr(typeIsCarAllowed, new PNot(valueIsCarNotAllowed))
    );
    RequestQuery query = new RequestQuery();
    query.setProperties(originalPropertyQuery);

    // When: disabling all queries related to `sign.value`
    Set<PQuery> disabledPQueries = disablePQueriesInRequest(query, pQueryMatchesPath("sign", "value"));

    // Then: disabled subqueries are about value
    assertEquals(2, disabledPQueries.size());
    assertTrue(disabledPQueries.containsAll(List.of(valueIs60, valueIsCarNotAllowed)));

    // And: original request was correctly mutated
    IPropertyQuery mutatedPropertyQuery = query.getProperties();
    assertSame(originalPropertyQuery, mutatedPropertyQuery,
        "Property query should be mutated in place - mutated and original query must refer to the same instance");
    // root is OR with 2 child nodes
    assertInstanceOf(POr.class, mutatedPropertyQuery);
    POr root = (POr) mutatedPropertyQuery;
    assertEquals(2, root.size());
    // first child is AND with 2 child nodes - including PTrue made from `valueIs60`
    assertInstanceOf(PAnd.class, root.get(0));
    PAnd andUnderRoot = (PAnd) root.get(0);
    assertEquals(2, andUnderRoot.size());
    assertEquals(typeIsSpeedLimit, andUnderRoot.get(0));
    assertEquals(PTrue.INSTANCE, andUnderRoot.get(1));
    // second child is OR with 2 child nodes - including mutated PNot with PFalse made from `valueIsCarNotAllowed`:
    assertInstanceOf(POr.class, root.get(1));
    POr orUnderRoot = (POr) root.get(1);
    assertEquals(2, orUnderRoot.size());
    assertEquals(typeIsCarAllowed, orUnderRoot.get(0));
    assertInstanceOf(PNot.class, orUnderRoot.get(1));
    PNot nestedPNot = (PNot) orUnderRoot.get(1);
    assertEquals(PFalse.INSTANCE, nestedPNot.getQuery());
  }

  private F1<Boolean, PQuery> pQueryMatchesPath(String... expectedPath) {
    return pQuery -> {
      StringList queryPath = pQuery.getProperty().getPath();
      if (expectedPath.length != queryPath.size()) {
        return false;
      }
      for (int i = 0; i < expectedPath.length; i++) {
        if (!expectedPath[i].equals(queryPath.get(i))) {
          return false;
        }
      }
      return true;
    };
  }
}