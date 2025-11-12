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

class PropertyOperationUtilTest {
    private final F1<Boolean, PQuery> dummyShouldDisable = pq -> false;

    @Test
    void shouldReturnEmptySetWhenPropertiesAbsent() {
        // Given
        RequestQuery query = new RequestQuery();

        // When
        Set<PQuery> disabledPQueries = disablePQueriesInRequest(query, dummyShouldDisable);

        // Then
        assertEquals(0, disabledPQueries.size());
        assertNull(query.getProperties());
    }

    @Test
    void shouldReduceTrivialAnd() {
        // Given
        RequestQuery query = new RequestQuery();
        IPropertyQuery propertyQuery = new PAnd(
                PTrue.INSTANCE,
                PTrue.INSTANCE
        );
        query.setProperties(propertyQuery);

        // When
        Set<PQuery> disabledPQueries = disablePQueriesInRequest(query, dummyShouldDisable);

        // Then
        assertEquals(0, disabledPQueries.size());
        assertNull(query.getProperties());
    }

    @Test
    void shouldReduceAlwaysFalseAnd() {
        // Given
        RequestQuery query = new RequestQuery();
        IPropertyQuery propertyQuery = new PAnd(
                PTrue.INSTANCE,
                PFalse.INSTANCE
        );
        query.setProperties(propertyQuery);

        // When
        Set<PQuery> disabledPQueries = disablePQueriesInRequest(query, dummyShouldDisable);

        // Then
        assertEquals(0, disabledPQueries.size());
        assertEquals(PFalse.INSTANCE, query.getProperties());
    }

    @Test
    void shouldReduceTrivialOr() {
        // Given
        RequestQuery query = new RequestQuery();
        IPropertyQuery propertyQuery = new POr(
                PFalse.INSTANCE,
                PFalse.INSTANCE
        );
        query.setProperties(propertyQuery);

        // When
        Set<PQuery> disabledPQueries = disablePQueriesInRequest(query, dummyShouldDisable);

        // Then
        assertEquals(0, disabledPQueries.size());
        assertEquals(PFalse.INSTANCE, query.getProperties());
    }

    @Test
    void shouldReduceAlwaysTrueOr() {
        // Given
        RequestQuery query = new RequestQuery();
        IPropertyQuery propertyQuery = new POr(
                PFalse.INSTANCE,
                PTrue.INSTANCE
        );
        query.setProperties(propertyQuery);

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
    void shouldDisablePOrWhenAllChildrenDisabled() {
        // Given
        PQuery valueIs60 = new PQuery(new Property("sign", "value"), DoubleOp.EQ, 60.0);
        PQuery valueIsCarNotAllowed = new PQuery(new Property("sign", "value"), StringOp.EQUALS, "car_not_allowed");
        IPropertyQuery originalPropertyQuery = new POr(
                valueIs60,
                valueIsCarNotAllowed
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
        assertNull(mutatedPropertyQuery);
    }

    @Test
    void shouldComposedPQueryInRequest() {
        // Given: request with dummy query with POp:
        // - query all speed limits of 60 AND "car allowed" signs
        // - "car allowed signs": signs with type `car_allowed` or value that is NOT `can_not_allowed`
        IPropertyQuery typeIsSpeedLimit = new PNot(new PQuery(new Property("sign", "type"), StringOp.EQUALS, "speed_limit"));
        PQuery valueIs60 = new PQuery(new Property("sign", "value"), DoubleOp.EQ, 60.0);
        PQuery typeIsCarAllowed = new PQuery(new Property("sign", "type"), StringOp.EQUALS, "car_allowed");
        PQuery valueIsCarNotAllowed = new PQuery(new Property("sign", "value"), StringOp.EQUALS, "car_not_allowed");
        IPropertyQuery originalPropertyQuery = new POr(
                new PAnd(new PNot(typeIsSpeedLimit), valueIs60),
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
        // first child is PNot
        PNot firstChild = assertInstanceOf(PNot.class, root.get(0));
        // second child is typeIsCarAllowed
        assertEquals(typeIsCarAllowed, root.get(1));
        // and typeIsSpeedLimit in firstChild
        assertNotNull(firstChild);
        assertEquals(typeIsSpeedLimit, firstChild.getQuery());
    }

    @Test
    void shouldDisableSimpleOr() {
        // Given
        POr root = new POr();
        root.add(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "a"));
        root.add(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "b"));
        RequestQuery query = new RequestQuery();
        query.setProperties(root);

        // When
        PropertyOperationUtil.disablePQueriesInRequest(query, pQueryMatchesPath("foo", "bar"));

        // Then
        assertNull(query.getProperties());
    }

    @Test
    void shouldDisableSimpleAnd() {
        // Given
        PAnd root = new PAnd();
        root.add(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "a"));
        root.add(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "b"));
        RequestQuery query = new RequestQuery();
        query.setProperties(root);

        // When
        PropertyOperationUtil.disablePQueriesInRequest(query, pQueryMatchesPath("foo", "bar"));

        // Then
        assertNull(query.getProperties());
    }

    @Test
    void shouldDisableSimpleNot() {
        // Given
        PNot root = new PNot();
        root.setQuery(new PQuery(new Property("foo", "bar"), StringOp.EQUALS, "a"));
        RequestQuery query = new RequestQuery();
        query.setProperties(root);

        // When
        PropertyOperationUtil.disablePQueriesInRequest(query, pQueryMatchesPath("foo", "bar"));

        // Then
        assertNull(query.getProperties());
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