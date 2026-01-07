package com.here.naksha.lib.handlers.util;

import com.here.naksha.lib.core.lambdas.F1;
import naksha.base.StringList;
import naksha.model.request.RequestQuery;
import naksha.model.request.query.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.here.naksha.lib.handlers.util.PropertyOperationUtil.disablePQueriesInRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class PropertyOperationUtilTest {
    private final F1<Boolean, PQuery> dummyShouldDisable = _ -> false;

    @Test
    void shouldNothingBeDisabledWhenPropertiesAbsent() {
        // Given
        RequestQuery query = new RequestQuery();
        assumeTrue(query.getProperties() == null);

        // When
        Set<PQuery> disabledPQueries = disablePQueriesInRequest(query, dummyShouldDisable);

        // Then
        assertEquals(0, disabledPQueries.size());
        assertNull(query.getProperties());
    }

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
        assertNull(query.getProperties());
    }

    @Test
    void shouldComposedPQueryInRequest() {
        // Given: request with dummy query with POp:
        // - query all speed limits of 60
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

        // And: request was correctly mutated
        IPropertyQuery newPropertyQuery = query.getProperties();
        assertNotNull(newPropertyQuery);
        // root is OR with 2 child nodes
        assertInstanceOf(POr.class, newPropertyQuery);
        POr root = (POr) newPropertyQuery;
        assertEquals(2, root.size());
        // first child is AND with 1 child node
        PAnd andUnderRoot = assertInstanceOf(PAnd.class, root.getFirst());
        assertEquals(1, andUnderRoot.size());
        assertEquals(typeIsSpeedLimit, andUnderRoot.getFirst());
        // second child is OR with 1 child node
        POr orUnderRoot = assertInstanceOf(POr.class, root.get(1));
        assertEquals(1, orUnderRoot.size());
        assertEquals(typeIsCarAllowed, orUnderRoot.getFirst());
    }

    @Test
    void shouldNotRemoveEmptyAnd() {
        // Given
        PAnd root = new PAnd();
        RequestQuery query = new RequestQuery();
        query.setProperties(root);

        // When
        disablePQueriesInRequest(query, dummyShouldDisable);

        // Then
        IPropertyQuery propertyQuery = query.getProperties();
        assertInstanceOf(PAnd.class, propertyQuery);
    }

    @Test
    void shouldNotRemoveEmptyOr() {
        // Given
        POr root = new POr();
        RequestQuery query = new RequestQuery();
        query.setProperties(root);

        // When
        disablePQueriesInRequest(query, dummyShouldDisable);

        // Then
        IPropertyQuery propertyQuery = query.getProperties();
        assertInstanceOf(POr.class, propertyQuery);
    }

    @Test
    void shouldRemoveOrWhenInnerQueriesDisabled() {
        // Given
        POr root = new POr();
        root.add(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "a"));
        root.add(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "b"));
        RequestQuery query = new RequestQuery();
        query.setProperties(root);

        // When
        disablePQueriesInRequest(query, pQueryMatchesPath("foo", "bar"));

        // Then
        assertNull(query.getProperties());
    }

    @Test
    void shouldRemoveAndWhenInnerQueriesDisabled() {
        // Given
        PAnd root = new PAnd();
        root.add(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "a"));
        root.add(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "b"));
        RequestQuery query = new RequestQuery();
        query.setProperties(root);

        // When
        disablePQueriesInRequest(query, pQueryMatchesPath("foo", "bar"));

        // Then
        assertNull(query.getProperties());
    }

    @Test
    void shouldRemoveDisabledChildFromAndAndKeepOther() {
        // Given
        PQuery a = new PQuery(new Property("a"), StringOp.EQUALS, "1");
        PQuery b = new PQuery(new Property("b"), StringOp.EQUALS, "2");

        // And
        RequestQuery query = new RequestQuery();
        query.setProperties(new PAnd(a, b));

        // When
        Set<PQuery> disabled = disablePQueriesInRequest(query, pQueryMatchesPath("a"));

        // Then
        assertEquals(Set.of(a), disabled);

        // And
        IPropertyQuery newRoot = query.getProperties();
        assertNotNull(newRoot);
        PAnd pAnd = assertInstanceOf(PAnd.class, newRoot);

        // And
        assertEquals(1, pAnd.size());
        assertEquals(b, pAnd.getFirst());
    }

    @Test
    void shouldRemoveDisabledChildFromOrAndKeepOther() {
        // Given
        PQuery a = new PQuery(new Property("a"), StringOp.EQUALS, "1");
        PQuery b = new PQuery(new Property("b"), StringOp.EQUALS, "2");

        // And
        RequestQuery query = new RequestQuery();
        query.setProperties(new POr(a, b));

        // When
        Set<PQuery> disabled = disablePQueriesInRequest(query, pQueryMatchesPath("a"));

        // Then
        assertEquals(Set.of(a), disabled);

        // And
        IPropertyQuery newRoot = query.getProperties();
        assertNotNull(newRoot);
        POr pOr = assertInstanceOf(POr.class, newRoot);

        // And
        assertEquals(1, pOr.size());
        assertEquals(b, pOr.getFirst());
    }

    @Test
    void shouldRemoveNotWhenInnerQueryDisabled() {
        // Given
        PQuery inner = new PQuery(new Property("a"), StringOp.EQUALS, "1");
        PNot root = new PNot(inner);

        // And
        RequestQuery query = new RequestQuery();
        query.setProperties(root);

        // When
        Set<PQuery> disabled = disablePQueriesInRequest(query, pQueryMatchesPath("a"));

        // Then
        assertEquals(Set.of(inner), disabled);
        assertNull(query.getProperties());
    }

    @Test
    void shouldNotDuplicateDisabledQueries() {
        // Given
        PQuery a = new PQuery(new Property("a"), StringOp.EQUALS, "1");

        RequestQuery query = new RequestQuery();
        query.setProperties(new PAnd(a, a));

        // When
        Set<PQuery> disabled = disablePQueriesInRequest(query, pQueryMatchesPath("a"));

        // Then
        assertEquals(1, disabled.size());
        assertTrue(disabled.contains(a));
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