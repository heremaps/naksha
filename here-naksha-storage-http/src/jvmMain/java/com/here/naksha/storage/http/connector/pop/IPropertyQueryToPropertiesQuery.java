package com.here.naksha.storage.http.connector.pop;

import com.here.naksha.lib.core.models.payload.events.PropertyQuery;
import com.here.naksha.lib.core.models.payload.events.PropertyQueryAnd;
import com.here.naksha.lib.core.models.payload.events.PropertyQueryOr;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.PAnd;
import naksha.model.request.query.PNot;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class IPropertyQueryToPropertiesQuery {

    private IPropertyQueryToPropertiesQuery() {}

    public static PropertyQueryOr toPopQueryOr(@NotNull IPropertyQuery query) {
        PropertyQueryOr or = new PropertyQueryOr();
        or.add(toPoPQueryAnd(query));
        return or;
    }

    public static PropertyQueryAnd toPoPQueryAnd(@NotNull IPropertyQuery query) {
        if (query instanceof PAnd) {
            PAnd pAnd = (PAnd) query;
            return and(pAnd);
        }
        PropertyQueryAnd and = new PropertyQueryAnd();
        and.add(iqToMultiValueComparison(query));
        return and;
    }


    private static PropertyQuery iqToMultiValueComparison(@NotNull IPropertyQuery q) {
        if (q instanceof POr) return or((POr) q);
        if (q instanceof PNot) return not((PNot) q);
        if (q instanceof PQuery) return simpleLeaf((PQuery) q);
        throw unsupported("Unsupported query node: " + q.getClass().getSimpleName());
    }

    private static PropertyQueryAnd and(@NotNull PAnd pAnd) {
        List<IPropertyQuery> children = asList(pAnd);
        assertHasAtLeastOneChild(children, "AND");
        PropertyQueryAnd acc = new PropertyQueryAnd();
        for (IPropertyQuery child : children) {
            PropertyQueryAnd childAnd = toPoPQueryAnd(child);
            acc.addAll(childAnd);
        }
        return acc;
    }

    private static PropertyQuery or(@NotNull POr pOr) {
        List<IPropertyQuery> children = asList(pOr);
        assertHasAtLeastOneChild(children, "OR");
        return children.stream()
                .map(IPropertyQueryToPropertiesQuery::iqToMultiValueComparison)
                .reduce(IPropertyQueryToPropertiesQuery::mergeOr)
                .orElseThrow(() -> new IllegalStateException("Unreachable: empty OR"));
    }

    private static PropertyQuery mergeOr(@NotNull PropertyQuery l, @NotNull PropertyQuery r) {
        if (!Objects.equals(l.getOperation(), r.getOperation())) {
            throw unsupported("Operators " + l.getOperation() + " and " + r.getOperation() + " combined in one OR");
        }
        if (!Objects.equals(l.getKey(), r.getKey())) {
            throw unsupported("Operator OR with two different keys: " + l.getKey() + " and " + r.getKey());
        }
        List<Object> values = new ArrayList<>(l.getValues().size() + r.getValues().size());
        values.addAll(l.getValues());
        values.addAll(r.getValues());
        return new PropertyQuery(l.getKey(), l.getOperation()).withValues(values);
    }

    private static PropertyQuery not(@NotNull PNot pNot) {
        IPropertyQuery inner = pNot.getQuery();
        PropertyQuery pq = iqToMultiValueComparison(inner);
        PropertyQuery.QueryOperation  op = pq.getOperation();
        PropertyQuery.QueryOperation flipped;
        if (op == PropertyQuery.QueryOperation.EQUALS) {
            flipped = PropertyQuery.QueryOperation.NOT_EQUALS;
        } else if (op == PropertyQuery.QueryOperation.NOT_EQUALS) {
            flipped = PropertyQuery.QueryOperation.EQUALS;
        } else {
            throw unsupported("Cannot negate operation: " + op);
        }
        return new PropertyQuery(pq.getKey(), flipped).withValues(pq.getValues());
    }

    private static PropertyQuery simpleLeaf(@NotNull PQuery leaf) {
        AnyOp anyOp = leaf.getOp();
        Object value = leaf.getValue();

        String key = leaf.getProperty().getPath().stream().map(Object::toString).collect(java.util.stream.Collectors.joining("."));
        String op = normalizeOp(anyOp);

        switch (op) {
            case "exists":
                return new PropertyQuery(key, PropertyQuery.QueryOperation.NOT_EQUALS)
                        .withValues(Collections.singletonList(null));
            case "=":
            case "equals":
            case "eq":
                assertHasValue(value);
                return new PropertyQuery(key, PropertyQuery.QueryOperation.EQUALS).withValues(Collections.singletonList(value));
            case "!=":
            case "not_equals":
                assertHasValue(value);
                return new PropertyQuery(key, PropertyQuery.QueryOperation.NOT_EQUALS).withValues(Collections.singletonList(value));

            case ">":
            case "gt":
                assertHasValue(value);
                return new PropertyQuery(key, PropertyQuery.QueryOperation.GREATER_THAN).withValues(Collections.singletonList(value));
            case ">=":
            case "gte":
                assertHasValue(value);
                return new PropertyQuery(key, PropertyQuery.QueryOperation.GREATER_THAN_OR_EQUALS).withValues(Collections.singletonList(value));
            case "<":
            case "lt":
                assertHasValue(value);
                return new PropertyQuery(key, PropertyQuery.QueryOperation.LESS_THAN).withValues(Collections.singletonList(value));
            case "<=":
            case "lte":
                assertHasValue(value);
                return new PropertyQuery(key, PropertyQuery.QueryOperation.LESS_THAN_OR_EQUALS).withValues(Collections.singletonList(value));

            case "@>":
            case "contains":
                assertHasValue(value);
                return new PropertyQuery(key, PropertyQuery.QueryOperation.CONTAINS).withValues(Collections.singletonList(value));

            case "startswith":
                throw unsupported("STARTS_WITH not supported");

            default:
                throw unsupported("Operation not supported: " + op);
        }
    }


    private static List<IPropertyQuery> asList(Object maybeList) {
        if (maybeList instanceof List) {
            @SuppressWarnings("unchecked")
            List<IPropertyQuery> list = (List<IPropertyQuery>) maybeList;
            return list;
        }
        throw unsupported("Expected a list-like node but got: " + maybeList.getClass().getSimpleName());
    }

    private static void assertHasAtLeastOneChild(List<IPropertyQuery> children, String opName) {
        if (children == null || children.isEmpty()) {
            throw unsupported(opName + " must have at least one child");
        }
    }

    private static void assertHasValue(@Nullable Object value) {
        if (value == null || value.toString().isEmpty()) {
            throw unsupported("Value is not present");
        }
    }

    private static String normalizeOp(@NotNull AnyOp op) {
        return Objects.toString(op, "").trim().toLowerCase(Locale.ROOT);
    }

    private static RuntimeException unsupported(String msg) {
        return new IPropertyQueryToQueryConversionException(msg);
    }

    public static class IPropertyQueryToQueryConversionException extends UnsupportedOperationException {
        public IPropertyQueryToQueryConversionException(String message) {
            super(message);
        }
    }
}