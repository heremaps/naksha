package com.here.naksha.app.service.http.ops;

import static com.here.naksha.common.http.apis.ApiParamsConst.SHORT_FEATURE_ID;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.AMPERSAND;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.COMMA;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.END;
import static com.here.naksha.lib.core.models.payload.events.QueryOperation.CONTAINS;
import static com.here.naksha.lib.core.models.payload.events.QueryOperation.EQUALS;
import static com.here.naksha.lib.core.models.payload.events.QueryOperation.GREATER_THAN;
import static com.here.naksha.lib.core.models.payload.events.QueryOperation.GREATER_THAN_OR_EQUALS;
import static com.here.naksha.lib.core.models.payload.events.QueryOperation.LESS_THAN;
import static com.here.naksha.lib.core.models.payload.events.QueryOperation.LESS_THAN_OR_EQUALS;
import static com.here.naksha.lib.core.models.payload.events.QueryOperation.NOT_EQUALS;

import com.here.naksha.lib.core.models.payload.events.QueryDelimiter;
import com.here.naksha.lib.core.models.payload.events.QueryOperation;
import com.here.naksha.lib.core.models.payload.events.QueryParameter;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.util.ValueList;
import java.util.*;

 import naksha.base.NakshaError;
 import naksha.base.NakshaException;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
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
import org.jetbrains.annotations.Nullable;

public class PropertyQueryUtil {

  private static final String NULL_PROP_VALUE = ".null";
  private static final String SHORT_PROP_PREFIX = "p.";
  private static final String FULL_PROP_PREFIX = Property.PROPERTIES + ".";
  private static final String SHORT_XYZ_PROP_PREFIX = "f.";
  private static final String FULL_XYZ_PROP_PREFIX = FULL_PROP_PREFIX + NakshaProperties.XYZ_KEY + ".";

  private static final Set<String> IGNORED_KEYS = Set.of(
      SHORT_FEATURE_ID // handled in FeatureIdQuery
  );

  private PropertyQueryUtil() {
  }

  /**
   * Function builds Property Query ({@link IPropertyQuery}) based on property key:value pairs supplied as API query parameter. We iterate
   * through all the parameters, exclude the keys that doesn't start with prefix "p." or "f." or "properties.", and interpret the others by
   * identifying the desired operation.
   * <p>
   * Multiple parameter keys result into AND list.
   * <br>
   * So, "p.prop_1=value_1&p.prop_2=value_2" will form AND condition as (p.prop_1=value_1 AND p.prop_2=value_2).
   * </p>
   *
   * <p>
   * Multiple parameter values concatenated with "," (COMMA) delimiter, will result into OR list.
   * <br>
   * So, "p.prop_1=value_1,value_11" will form OR condition as (p.prop_1=value_1 OR p.prop_1=value_11).
   * <br>
   * NOTE that OR condition is supported only for the same one key and multiple values only, not for multiple key value pairs. The reason is
   * to prevent complication when transformation between property search and other types of search like tag search is employed (for example
   * through Source ID Handler). So, "?p.property_name_1=value_1 OR p.@ns:com:here:mom:meta.sourceId=abc" through Source ID Handler would
   * then become an OR between a property search (the first clause unchanged) and a tag search (the second clause transformed), which is not
   * supported. Only AND relation is supported between different types of search (property, tag, spatial,...).
   * </p>
   *
   * @param queryParams API query parameter from where property search params need to be extracted
   * @return property query that can be used as part of {@link naksha.model.request.RequestQuery}
   */
  public static @Nullable IPropertyQuery propertyQueryFromParams(final @Nullable QueryParameterList queryParams) {
    if (queryParams == null) {
      return null;
    }
    // global initialization
    final List<IPropertyQuery> globalOpList = new ArrayList<>();
    // iterate through each parameter
    for (final QueryParameter param : queryParams) {
      // prepare property search operation
      final IPropertyQuery crtOp = preparePropertySearchOperation(param);
      // add current search operation to global list
      if (crtOp != null) {
        globalOpList.add(crtOp);
      }
    }

    if (globalOpList.isEmpty()) {
      return null;
    }
    // return single operation or AND list (in case of multiple operations)
    if (globalOpList.size() > 1) {
      PAnd combined = new PAnd();
      combined.addAll(globalOpList);
      return combined;
    }
    return globalOpList.get(0);
  }

  private static @Nullable String[] expandKeyToRealJsonPath(final @NotNull String key) {
    final StringBuilder str = new StringBuilder();
    if (key.startsWith(SHORT_PROP_PREFIX)) {
      str.append(FULL_PROP_PREFIX).append(key.substring(SHORT_PROP_PREFIX.length()));
    } else if (IGNORED_KEYS.contains(key)) {
      return null; // exclude ignored keys
    } else if (key.equals(SHORT_FEATURE_ID)) {
      str.append(NakshaFeature.ID_KEY);
    } else if (key.startsWith(SHORT_XYZ_PROP_PREFIX)) {
      str.append(FULL_XYZ_PROP_PREFIX).append(key.substring(SHORT_XYZ_PROP_PREFIX.length()));
    } else if (key.startsWith(FULL_PROP_PREFIX)) {
      str.append(key);
    } else {
      return null; // excluding non-supported prop-search key
    }
    return str.toString().split("\\.");
  }

  private static @Nullable IPropertyQuery preparePropertySearchOperation(final @NotNull QueryParameter param) {
    // extract param key, operation, values, delimiters
    final String propKey = param.key();
    final QueryOperation operation = param.op();
    final ValueList propValues = param.values();
    final List<QueryDelimiter> delimiters = param.valuesDelimiter();

    // global operation list if multiple values are supplied for this property key
    final List<IPropertyQuery> gOpList = new ArrayList<>();

    // expand key if needed (e.g. p.prop_1 should be properties.prop_1)
    final String[] propPath = expandKeyToRealJsonPath(propKey);
    if (propPath == null) {
      return null;
    }

    // iterate through all given values for a key
    int delimIdx = 0;
    for (final Object value : propValues) {
      if (value == null) {
        throw new NakshaException(
            NakshaError.ILLEGAL_ARGUMENT, "Unsupported null value for key %s".formatted(propKey));
      }
      // validate delimiter ("," to be taken as OR operation)
      final QueryDelimiter delimiter = delimiters.get(delimIdx++);
      if (delimiter != AMPERSAND && delimiter != COMMA && delimiter != END) {
        throw new NakshaException(
            NakshaError.ILLEGAL_ARGUMENT, "Unsupported delimiter %s for key %s".formatted(delimiter, propKey));
      }
      // prepare property operation for crt value
      final IPropertyQuery crtOp;
      if (value instanceof String str) {
        crtOp = mapAPIOperationToPropertyOperation(operation, propPath, str);
      } else if (value instanceof Number num) {
        crtOp = mapAPIOperationToPropertyOperation(operation, propPath, num);
      } else if (value instanceof Boolean bool) {
        crtOp = mapAPIOperationToPropertyOperation(operation, propPath, bool);
      } else {
        throw new NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "Unsupported value type %s for key %s"
                .formatted(value.getClass().getName(), propKey));
      }
      // add current operation to global list
      gOpList.add(crtOp);
    }

    // return single operation or OR list (in case of multiple operations)
    if (gOpList.size() > 1) {
      POr combinedOr = new POr();
      combinedOr.addAll(gOpList);
      return combinedOr;
    }
    return gOpList.get(0);
  }

  private static @NotNull IPropertyQuery mapAPIOperationToPropertyOperation(
      final @NotNull QueryOperation operation,
      final @NotNull String[] propPath,
      final @NotNull String value
  ) {
    if (operation == EQUALS) {
      // check if it is NULL operation
      if (NULL_PROP_VALUE.equals(value)) {
        return new PNot(propertyExistsQuery(propPath));
      } else {
        return propertyEqualsQuery(propPath, value);
      }
    } else if (operation == NOT_EQUALS) {
      // check if it is NOT NULL operation
      if (NULL_PROP_VALUE.equals(value)) {
        return propertyExistsQuery(propPath);
      } else {
        return new PNot(propertyEqualsQuery(propPath, value));
      }
    } else if (operation == CONTAINS) {
      // if string represents JSON object, then we automatically add JSON array comparison
      if (value.startsWith("{") && value.endsWith("}")) {
        return new POr(
            propertyContainsQuery(propPath, value),
            propertyContainsQuery(propPath, "[%s]".formatted(value))
        );
      } else {
        return propertyContainsQuery(propPath, value);
      }
    } else {
      throw new NakshaException(
          NakshaError.ILLEGAL_ARGUMENT,
          "Unsupported operation %s with string value %s".formatted(operation.name, value));
    }
  }

  private static @NotNull IPropertyQuery mapAPIOperationToPropertyOperation(
      final @NotNull QueryOperation operation, final @NotNull String[] propPath, final @NotNull Number value) {
    if (operation == EQUALS) {
      return new PQuery(new Property(propPath), DoubleOp.EQ, value);
    } else if (operation == NOT_EQUALS) {
      return new PNot(new PQuery(new Property(propPath), DoubleOp.EQ, value));
    } else if (operation == GREATER_THAN) {
      return new PQuery(new Property(propPath), DoubleOp.GT, value);
    } else if (operation == GREATER_THAN_OR_EQUALS) {
      return new PQuery(new Property(propPath), DoubleOp.GTE, value);
    } else if (operation == LESS_THAN) {
      return new PQuery(new Property(propPath), DoubleOp.LT, value);
    } else if (operation == LESS_THAN_OR_EQUALS) {
      return new PQuery(new Property(propPath), DoubleOp.LTE, value);
    } else if (operation == CONTAINS) {
      return new PQuery(new Property(propPath), DoubleOp.CONTAINS, value);
    } else {
      throw new NakshaException(
          NakshaError.ILLEGAL_ARGUMENT,
          "Unsupported operation %s with numeric value %s".formatted(operation.name, value));
    }
  }

  private static @NotNull IPropertyQuery mapAPIOperationToPropertyOperation(
      final @NotNull QueryOperation operation, final @NotNull String[] propPath, final @NotNull Boolean value) {
    if ((operation == EQUALS && value) || (operation == NOT_EQUALS && !value)) {
      return new PQuery(new Property(propPath), AnyOp.IS_TRUE);
    } else if ((operation == EQUALS && !value) || (operation == NOT_EQUALS && value)) {
      return new PQuery(new Property(propPath), AnyOp.IS_FALSE);
    } else if (operation == CONTAINS) {
      return new PQuery(new Property(propPath), AnyOp.CONTAINS, value);
    } else {
      throw new NakshaException(
          NakshaError.ILLEGAL_ARGUMENT,
          "Unsupported operation %s with boolean value %s".formatted(operation.name, value));
    }
  }

  private static @NotNull PQuery propertyExistsQuery(final @NotNull String[] propPath) {
    return new PQuery(new Property(propPath), AnyOp.EXISTS);
  }

  private static @NotNull PQuery propertyEqualsQuery(final @NotNull String[] propPath, String value) {
    return new PQuery(new Property(propPath), StringOp.EQUALS, value);
  }

  private static @NotNull PQuery propertyContainsQuery(final @NotNull String[] propPath, String value) {
    return new PQuery(new Property(propPath), StringOp.CONTAINS, value);
  }
}
