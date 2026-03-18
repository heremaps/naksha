package com.here.naksha.storage.http.ffw;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.DoubleOp;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.PAnd;
import naksha.model.request.query.PNot;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;

/**
 * A robust utility class to convert an {@link IPropertyQuery} object into a URL query parameter string. This class handles various logical
 * operators (AND, OR, NOT) and property operations (e.g., equals, greater than, contains).
 */
public final class IPropertyQueryToQueryConverter {

  private static final String OP_EQ = "=";
  private static final String OP_NEQ = "!=";
  private static final String OP_GT = "=gt=";
  private static final String OP_GTE = "=gte=";
  private static final String OP_LT = "=lt=";
  private static final String OP_LTE = "=lte=";
  private static final String OP_CONTAINS = "=cs=";

  private static final String DELIMITER_AND = "&";
  private static final String DELIMITER_OR = ",";
  private static final String DELIMITER_DOT = ".";

  private static final String VALUE_NULL = ".null";
  private static final String VALUE_TRUE = "true";
  private static final String VALUE_FALSE = "false";

  private static final class EffectiveQueryParts {
    private final String path;
    private final String operator;
    private final String valueString;

    private EffectiveQueryParts(String path, String operator, String valueString) {
      this.path = path;
      this.operator = operator;
      this.valueString = valueString;
    }

    private String getPath() {
      return path;
    }

    private String getOperator() {
      return operator;
    }

    private String getValueString() {
      return valueString;
    }
  }

  private IPropertyQueryToQueryConverter() {
  }

  /**
   * Converts an IPropertyQuery object into a URL query parameter string. This is the main entry point for the conversion.
   *
   * @param query The IPropertyQuery object to convert.
   * @return A URL-formatted query string.
   */
  public static String convert(IPropertyQuery query) {
    if (query instanceof PAnd) {
      return convertPAnd((PAnd) query);
    }
    if (query instanceof POr) {
      return convertPOr((POr) query);
    }
    if (query instanceof PNot || query instanceof PQuery) {
      EffectiveQueryParts parts = getEffectiveParts(query);
      return parts.getPath() + parts.getOperator() + parts.getValueString();
    }
    throw new UnsupportedOperationException("Unsupported query type: " + query.getClass().getSimpleName());
  }

  private static String convertPAnd(PAnd query) {
    return query.stream()
        .filter(Objects::nonNull)
        .map(IPropertyQueryToQueryConverter::convert)
        .collect(Collectors.joining(DELIMITER_AND));
  }

  private static String convertPOr(POr query) {
      if (query.isEmpty()) {
          return "";
      }

    List<IPropertyQuery> flattenedChildren = new ArrayList<>();
    for (IPropertyQuery child : query) {
      if (child instanceof POr) {
        POr innerOr = (POr) child;
        flattenedChildren.addAll(innerOr);
      } else {
        flattenedChildren.add(child);
      }
    }

    EffectiveQueryParts firstParts = getEffectiveParts(flattenedChildren.get(0));
    final String referencePath = firstParts.getPath();
    final String referenceOpString = firstParts.getOperator();

    String values = flattenedChildren.stream()
        .map(q -> {
          EffectiveQueryParts currentParts = getEffectiveParts(q);
          if (!currentParts.getPath().equals(referencePath) || !currentParts.getOperator().equals(referenceOpString)) {
            throw new IllegalStateException("All queries in a POr must resolve to the same property and operator symbol.");
          }
          return currentParts.getValueString();
        })
        .collect(Collectors.joining(DELIMITER_OR));

    return referencePath + referenceOpString + values;
  }

  private static EffectiveQueryParts getEffectiveParts(IPropertyQuery query) {
    if (query instanceof PQuery) {
      PQuery p = (PQuery) query;
      final String path = getEncodedPath(p.getProperty());
      final AnyOp op = p.getOp();
        if (op == AnyOp.EXISTS || op == AnyOp.IS_NOT_NULL) {
            return new EffectiveQueryParts(path, OP_NEQ, VALUE_NULL);
        }
        if (op == AnyOp.IS_NULL) {
            return new EffectiveQueryParts(path, OP_EQ, VALUE_NULL);
        }
        if (op == AnyOp.IS_TRUE) {
            return new EffectiveQueryParts(path, OP_EQ, VALUE_TRUE);
        }
        if (op == AnyOp.IS_FALSE) {
            return new EffectiveQueryParts(path, OP_EQ, VALUE_FALSE);
        }

      final String opString = getOperatorString(op);
      final String valueString = (p.getValue() == null) ? VALUE_NULL : urlEncode(p.getValue());
      return new EffectiveQueryParts(path, opString, valueString);
    }
    if (query instanceof PNot) {
      PNot n = (PNot) query;
      if (n.getQuery() instanceof PQuery) {
        PQuery p = (PQuery) n.getQuery();
        final String path = getEncodedPath(p.getProperty());
        final AnyOp op = p.getOp();
        if (op == AnyOp.EXISTS || op == AnyOp.IS_NOT_NULL) {
            return new EffectiveQueryParts(path, OP_EQ, VALUE_NULL);
        }
        if (op == AnyOp.IS_NULL) {
            return new EffectiveQueryParts(path, OP_NEQ, VALUE_NULL);
        }
        if (op == AnyOp.IS_TRUE) {
            return new EffectiveQueryParts(path, OP_EQ, VALUE_FALSE);
        }
        if (op == AnyOp.IS_FALSE) {
            return new EffectiveQueryParts(path, OP_EQ, VALUE_TRUE);
        }

        final String opString = getInvertedOperatorString(op);
        final String valueString = (p.getValue() == null) ? VALUE_NULL : urlEncode(p.getValue());
        return new EffectiveQueryParts(path, opString, valueString);
      }
    }
    throw new IllegalStateException("Unsupported query type for part resolution: " + query.getClass().getSimpleName());
  }

  private static String getEncodedPath(Property property) {
    return property.getPath().stream()
        .map(IPropertyQueryToQueryConverter::urlEncode)
        .collect(Collectors.joining(DELIMITER_DOT));
  }

  private static String urlEncode(Object obj) {
    return URLEncoder.encode(obj.toString(), StandardCharsets.UTF_8);
  }

  private static String getOperatorString(AnyOp op) {
      if (op == StringOp.EQUALS || op == DoubleOp.EQ || op == AnyOp.IS_TRUE || op == AnyOp.IS_FALSE) {
          return OP_EQ;
      }
      if (op == StringOp.NOT_EQUALS || op == DoubleOp.NE) {
          return OP_NEQ;
      }
      if (op == DoubleOp.GT) {
          return OP_GT;
      }
      if (op == DoubleOp.GTE) {
          return OP_GTE;
      }
      if (op == DoubleOp.LT) {
          return OP_LT;
      }
      if (op == DoubleOp.LTE) {
          return OP_LTE;
      }
      if (op == StringOp.CONTAINS || op == AnyOp.CONTAINS) {
          return OP_CONTAINS;
      }
    throw new UnsupportedOperationException(
        "Operator '" + op.getClass().getSimpleName() + "' is not supported for value-based conversion.");
  }

  private static String getInvertedOperatorString(AnyOp op) {
      if (op == StringOp.EQUALS || op == DoubleOp.EQ) {
          return OP_NEQ;
      }
      if (op == StringOp.NOT_EQUALS || op == DoubleOp.NE) {
          return OP_EQ;
      }
    throw new UnsupportedOperationException("Operator '" + op.getClass().getSimpleName() + "' cannot be negated.");
  }
}