/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.app.service.http.apis;

import static com.here.naksha.app.service.http.apis.ApiParams.TAGS;
import static com.here.naksha.app.service.http.apis.ApiParams.TILE_TYPE_QUADKEY;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.*;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.COMMA;
import static com.here.naksha.lib.core.models.payload.events.QueryOperation.*;

import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.payload.events.QueryDelimiter;
import com.here.naksha.lib.core.models.payload.events.QueryOperation;
import com.here.naksha.lib.core.models.payload.events.QueryParameter;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.util.ValueList;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.vividsolutions.jts.geom.Geometry;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApiUtil {

  private static final int NONE = 0, OR = 1, AND = 2;
  private static final String NULL_PROP_VALUE = ".null";

  public static @NotNull SOp buildOperationForTile(final @NotNull String tileType, final @NotNull String tileId) {
    try {
      if (!tileType.equals(TILE_TYPE_QUADKEY)) {
        throw new XyzErrorException(XyzError.ILLEGAL_ARGUMENT, "Tile type " + tileType + " not supported");
      }
      final Geometry geo =
          WebMercatorTile.forQuadkey(tileId).getAsPolygon().getGeometry();
      return SOp.intersects(geo);
    } catch (IllegalArgumentException ex) {
      throw new XyzErrorException(XyzError.ILLEGAL_ARGUMENT, ex.getMessage());
    }
  }

  public static @NotNull SOp buildOperationForBBox(
      final double west, final double south, final double east, final double north) {
    return SOp.intersects(RequestHelper.createBBoxEnvelope(west, south, east, north));
  }

  /**
   * Function builds Property Operation (POp) based on "tags" supplied as API query parameter.
   * We iterate through all the tag combination values provided in the query param.
   *   For every tag combination concatenated with "," (COMMA) delimiter, we prepare OR list
   *   For every tag combination concatenated with "+" (PLUS) delimiter, we prepare AND list
   *   For any tag not part of any combination, we take it as part of OR condition
   *
   * So, for example if the input tag list is like this:
   *   tags = one
   *   tags = two,three
   *   tags = four+five
   *   tags = six,seven,eight+nine
   *   tags = ten+eleven,twelve,thirteen
   *   tags = fourteen
   *   Then, we generate
   * OR operation between:
   *   - one
   *   - (two OR three)
   *   - (four AND five)
   *   - (six OR seven)
   *   - (eight AND nine)
   *   - (ten AND eleven)
   *   - (twelve OR thirteen)
   *   - fourteen
   *
   * @param queryParams API query parameter from where "tags" needs to be extracted
   * @return POp property operation that can be used as part of {@link ReadRequest}
   */
  public static @Nullable POp buildOperationForTagsQueryParam(final @Nullable QueryParameterList queryParams) {
    if (queryParams == null) return null;
    QueryParameter tagParams = queryParams.get(TAGS);
    if (tagParams == null) return null;

    // global initialization
    final List<POp> globalOpList = new ArrayList<>();

    while (tagParams != null && tagParams.hasValues()) {
      // get list of all tag tokens and respective delimiters
      final ValueList tagTokenList = tagParams.values();
      final List<QueryDelimiter> delimList = tagParams.valuesDelimiter();
      // iterate through tag tokens and add them to OR / AND / Global list depending on delimiter
      // loop variable initialization
      int crtOp = NONE;
      List<String> orList = null;
      List<String> andList = null;
      int delimIdx = 0;
      for (final Object obj : tagTokenList) {
        if (obj == null) {
          if (crtOp == NONE) { // we skip null value if it is at the start of operation
            delimIdx++;
            continue;
          } else { // null value in middle of AND/OR operation not allowed
            throw new XyzErrorException(
                XyzError.ILLEGAL_ARGUMENT, "Empty tag not allowed - " + tagTokenList);
          }
        }
        final String tag = (String) obj;
        if (tag.isEmpty()) {
          if (crtOp == NONE) { // we skip empty value if it is at the start of operation
            delimIdx++;
            continue;
          } else { // empty value in middle of AND/OR operation not allowed
            throw new XyzErrorException(
                XyzError.ILLEGAL_ARGUMENT, "Empty tag not allowed - " + tagTokenList);
          }
        }
        final QueryDelimiter delimiter = delimList.get(delimIdx++);
        if (delimiter != AMPERSAND && delimiter != END && delimiter != COMMA && delimiter != PLUS) {
          throw new XyzErrorException(
              XyzError.ILLEGAL_ARGUMENT, "Invalid delimiter " + delimiter + " for parameter " + TAGS);
        }
        // is it start of new operation?
        if (crtOp == NONE) {
          if (delimiter == AMPERSAND || delimiter == END) {
            // this is the only tag. add this tag to global list straightaway
            addTagsToGlobalOpList(OR, globalOpList, tag);
          } else if (delimiter == COMMA) {
            // open OR operation and add this tag to OR list
            crtOp = OR;
            orList = addTagToList(orList, tag);
          } else if (delimiter == PLUS) {
            // open AND operation and add this tag to AND list
            crtOp = AND;
            andList = addTagToList(andList, tag);
          }
        }
        // is current ongoing operation OR?
        else if (crtOp == OR) {
          if (delimiter == AMPERSAND || delimiter == END) {
            // add this tag to OR list, add OR list to global list
            orList = addTagToList(orList, tag);
            addTagsToGlobalOpList(crtOp, globalOpList, orList.toArray(String[]::new));
            // and reset operation
            crtOp = NONE;
            orList = null;
            andList = null;
          } else if (delimiter == COMMA) {
            // add this tag to OR list
            orList = addTagToList(orList, tag);
          } else if (delimiter == PLUS) {
            // change of operation sequence. add crt OR list to global list. reset OR list
            addTagsToGlobalOpList(crtOp, globalOpList, orList.toArray(String[]::new));
            orList = null;
            // open new AND operation and add this tag to AND list
            crtOp = AND;
            andList = addTagToList(andList, tag);
          }
        }
        // current ongoing operation is AND
        else {
          if (delimiter == AMPERSAND || delimiter == END) {
            // add this tag to AND list, add AND list to global list
            andList = addTagToList(andList, tag);
            addTagsToGlobalOpList(crtOp, globalOpList, andList.toArray(String[]::new));
            // and reset operation
            crtOp = NONE;
            orList = null;
            andList = null;
          } else if (delimiter == PLUS) {
            // add this tag to AND list
            andList = addTagToList(andList, tag);
          } else if (delimiter == COMMA) {
            // change of operation sequence. add this tag to AND list. add AND list to global list
            andList = addTagToList(andList, tag);
            addTagsToGlobalOpList(crtOp, globalOpList, andList.toArray(String[]::new));
            // reset operation
            crtOp = NONE;
            orList = null;
            andList = null;
          }
        }
      }
      tagParams = tagParams.next();
    }

    // return single operation or OR list (in case of multiple operations)
    final POp[] allTagOpArr = globalOpList.toArray(POp[]::new);
    return (allTagOpArr.length > 1) ? POp.or(allTagOpArr) : allTagOpArr[0];
  }

  private static @NotNull List<String> addTagToList(final @Nullable List<String> tagList, final @NotNull String tag) {
    final List<String> retList = (tagList == null) ? new ArrayList<>() : tagList;
    retList.add(tag);
    return retList;
  }

  private static void addTagsToGlobalOpList(int crtOp, final @NotNull List<POp> gList, String... tags) {
    if (tags == null || tags.length < 1) return;
    if (crtOp != OR && crtOp != AND) return;
    // Do we have only one tag? then use EXISTS operation
    if (tags.length == 1) {
      gList.add(POp.exists(PRef.tag(XyzNamespace.normalizeTag(tags[0]))));
      return;
    }
    // We have multiple tags, so use OR / AND operation
    final POp[] tagOpArr = new POp[tags.length];
    for (int i = 0; i < tags.length; i++) {
      tagOpArr[i] = POp.exists(PRef.tag(XyzNamespace.normalizeTag(tags[i])));
    }
    if (crtOp == OR) {
      gList.add(POp.or(tagOpArr));
    } else {
      gList.add(POp.and(tagOpArr));
    }
  }

  /**
   * Function builds Property Operation (POp) based on property key:value pairs supplied as API query parameter.
   * We iterate through all the parameters, exclude the keys that match with provided excludeKeys,
   * and interpret the others by identifying the desired operation.
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
   * </p>
   *
   * @param queryParams API query parameter from where property search params need to be extracted
   * @param excludeKeys List of param keys to be excluded (i.e. not part of property search)
   * @return POp property operation that can be used as part of {@link ReadRequest}
   */
  public static @Nullable POp buildOperationForPropertySearchParams(
      final @Nullable QueryParameterList queryParams, final @Nullable List<String> excludeKeys) {
    if (queryParams == null) return null;
    // global initialization
    final List<POp> globalOpList = new ArrayList<>();
    // iterate through each parameter
    for (final QueryParameter param : queryParams) {
      // extract param key, operation, values, delimiters
      final String key = param.key();
      final QueryOperation operation = param.op();
      final ValueList values = param.values();
      final List<QueryDelimiter> delimiters = param.valuesDelimiter();

      // is this key to be excluded?
      if (excludeKeys != null && excludeKeys.contains(key)) continue;
      // prepare property search operation
      final POp crtOp = preparePropertySearchOperation(operation, key, values, delimiters);
      // add current search operation to global list
      globalOpList.add(crtOp);
    }

    if (globalOpList.isEmpty()) return null;
    // return single operation or AND list (in case of multiple operations)
    final POp[] allPOpArr = globalOpList.toArray(POp[]::new);
    return (allPOpArr.length > 1) ? POp.and(allPOpArr) : allPOpArr[0];
  }

  private static @NotNull String[] expandKeyToRealJsonPath(final @NotNull String key) {
    return key.split("\\.");
  }

  private static @NotNull POp preparePropertySearchOperation(
      final @NotNull QueryOperation operation,
      final @NotNull String propKey,
      final @NotNull ValueList propValues,
      final @NotNull List<QueryDelimiter> delimiters) {
    // global operation list if multiple values are supplied for this property key
    final List<POp> gOpList = new ArrayList<>();

    // TODO : expand key if needed (e.g. p.prop_1 should be properties.prop_1)
    final String[] propPath = expandKeyToRealJsonPath(propKey);

    // iterate through all given values for a key
    int delimIdx = 0;
    for (final Object value : propValues) {
      if (value == null) {
        throw new XyzErrorException(
            XyzError.ILLEGAL_ARGUMENT, "Unsupported null value for key %s".formatted(propKey));
      }
      // validate delimiter ("," to be taken as OR operation)
      final QueryDelimiter delimiter = delimiters.get(delimIdx++);
      if (delimiter != AMPERSAND && delimiter != COMMA && delimiter != END) {
        throw new XyzErrorException(
            XyzError.ILLEGAL_ARGUMENT, "Unsupported delimiter %s for key %s".formatted(delimiter, propKey));
      }
      // prepare property operation for crt value
      final POp crtOp;
      if (value instanceof String str) {
        crtOp = mapAPIOperationToPropertyOperation(operation, propPath, str);
      } else if (value instanceof Number num) {
        crtOp = mapAPIOperationToPropertyOperation(operation, propPath, num);
      } else if (value instanceof Boolean bool) {
        crtOp = mapAPIOperationToPropertyOperation(operation, propPath, bool);
      } else {
        throw new XyzErrorException(
            XyzError.ILLEGAL_ARGUMENT,
            "Unsupported value type %s for key %s"
                .formatted(value.getClass().getName(), propKey));
      }
      // add current operation to global list
      gOpList.add(crtOp);
    }

    // return single operation or OR list (in case of multiple operations)
    final POp[] allPOpArr = gOpList.toArray(POp[]::new);
    return (allPOpArr.length > 1) ? POp.or(allPOpArr) : allPOpArr[0];
  }

  private static @NotNull POp mapAPIOperationToPropertyOperation(
      final @NotNull QueryOperation operation, final @NotNull String[] propPath, final @NotNull String value) {
    if (operation == EQUALS) {
      // check if it is NULL operation
      if (NULL_PROP_VALUE.equals(value)) {
        return POp.not(POp.exists(new NonIndexedPRef(propPath)));
      } else {
        return POp.eq(new NonIndexedPRef(propPath), value);
      }
    } else if (operation == NOT_EQUALS) {
      // check if it is NOT NULL operation
      if (NULL_PROP_VALUE.equals(value)) {
        return POp.exists(new NonIndexedPRef(propPath));
      } else {
        return POp.not(POp.eq(new NonIndexedPRef(propPath), value));
      }
    } else if (operation == CONTAINS) {
      // if string represents JSON object, then we automatically add JSON array comparison
      if (value.startsWith("{") && value.endsWith("}")) {
        return POp.or(
            POp.contains(new NonIndexedPRef(propPath), value),
            POp.contains(new NonIndexedPRef(propPath), "[%s]".formatted(value)));
      } else {
        return POp.contains(new NonIndexedPRef(propPath), value);
      }
    } else {
      throw new XyzErrorException(
          XyzError.ILLEGAL_ARGUMENT,
          "Unsupported operation %s with string value %s".formatted(operation.name, value));
    }
  }

  private static @NotNull POp mapAPIOperationToPropertyOperation(
      final @NotNull QueryOperation operation, final @NotNull String[] propPath, final @NotNull Number value) {
    if (operation == EQUALS) {
      return POp.eq(new NonIndexedPRef(propPath), value);
    } else if (operation == NOT_EQUALS) {
      return POp.not(POp.eq(new NonIndexedPRef(propPath), value));
    } else if (operation == GREATER_THAN) {
      return POp.gt(new NonIndexedPRef(propPath), value);
    } else if (operation == GREATER_THAN_OR_EQUALS) {
      return POp.gte(new NonIndexedPRef(propPath), value);
    } else if (operation == LESS_THAN) {
      return POp.lt(new NonIndexedPRef(propPath), value);
    } else if (operation == LESS_THAN_OR_EQUALS) {
      return POp.lte(new NonIndexedPRef(propPath), value);
    } else if (operation == CONTAINS) {
      return POp.contains(new NonIndexedPRef(propPath), value);
    } else {
      throw new XyzErrorException(
          XyzError.ILLEGAL_ARGUMENT,
          "Unsupported operation %s with numeric value %s".formatted(operation.name, value));
    }
  }

  private static @NotNull POp mapAPIOperationToPropertyOperation(
      final @NotNull QueryOperation operation, final @NotNull String[] propPath, final @NotNull Boolean value) {
    if (operation == EQUALS) {
      return POp.eq(new NonIndexedPRef(propPath), value);
    } else if (operation == NOT_EQUALS) {
      return POp.not(POp.eq(new NonIndexedPRef(propPath), value));
    } else if (operation == CONTAINS) {
      return POp.contains(new NonIndexedPRef(propPath), value);
    } else {
      throw new XyzErrorException(
          XyzError.ILLEGAL_ARGUMENT,
          "Unsupported operation %s with boolean value %s".formatted(operation.name, value));
    }
  }
}
