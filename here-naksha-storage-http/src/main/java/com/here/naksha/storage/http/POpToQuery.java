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
package com.here.naksha.storage.http;

import static com.here.naksha.lib.core.models.storage.POpType.*;

import com.here.naksha.lib.core.models.storage.OpType;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.POpType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class POpToQuery {
  static String getQueryFromPop(@NotNull POp pop) {
    if (pop.op().equals(AND)) return and(pop.children());
    else if (pop.op().equals(OR)) return or(pop.children());
    else return or(List.of(pop));
  }

  private static String and(List<POp> children) {
    return children.stream().map(POpToQuery::getQueryFromPop).collect(Collectors.joining("&"));
  }

  private static String or(List<POp> children) {
    POp firstChild = children.get(0);
    if (isNotEqOp(firstChild) || isNotNullOp(firstChild)) return orWithNotEqSign(children);
    else if (isEqOp(firstChild) || isANullOp(firstChild)) return orWithEqSign(children);
    else if (firstChild.op() == OR || firstChild.op() == CONTAINS) return orWithContainsSign(children);
    else return orWithOtherSigns(children);
  }

  private static boolean isNotEqOp(POp pOp) {
    try {
      return pOp.op() == OpType.NOT && pOp.children().get(0).op() == EQ;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isNotNullOp(POp pOp) {
    return pOp.op() == EXISTS;
  }

  private static boolean isEqOp(POp pOp) {
    return pOp.op() == EQ;
  }

  private static boolean isANullOp(POp pOp) {
    try {
      return pOp.op() == OpType.NOT && pOp.children().get(0).op() == EXISTS;
    } catch (Exception e) {
      return false;
    }
  }

  private static String orWithNotEqSign(List<POp> children) {
    String path = getPath(children.get(0));
    String values = mapToCommaSeparatedValues(children, child -> {
      if (isNotNullOp(child)) return ".null";
      else if (isNotEqOp(child)) return getValue(child.children().get(0));
      else throw new UnsupportedOperationException("op not compat with !=");
    });

    return path + "!=" + values;
  }

  private static String orWithEqSign(List<POp> children) {
    String path = getPath(children.get(0));
    String values = mapToCommaSeparatedValues(children, child -> {
      if (isANullOp(child)) return ".null";
      else if (isEqOp(child)) return getValue(child);
      else throw new UnsupportedOperationException("op not compat with =");
    });

    return path + "=" + values;
  }

  private static String orWithContainsSign(List<POp> children) {
    POp firstChild = children.get(0);
    String expectedPath = getPath(firstChild);
    String values = mapToCommaSeparatedValues(children, child -> {
      if (child.op() == OR)
        return mapToCommaSeparatedValues(child.children(), grandchild -> {
          if (grandchild.op() != CONTAINS) throw new UnsupportedOperationException();
          return getValue(grandchild);
        });
      else if (child.op() == CONTAINS) return getValue(child);
      else throw new UnsupportedOperationException();
    });
    return expectedPath + "=cs=" + values;
  }

  private static String orWithOtherSigns(List<POp> children) {
    POp firstChild = children.get(0);
    String expectedPath = getPath(firstChild);
    OpType expectedOperation = firstChild.op();
    if (expectedOperation instanceof POpType pOpType) {
      String operator = getOperator(pOpType);
      String values = mapToCommaSeparatedValues(children, child -> {
        if (child.op() != expectedOperation)
          throw new UnsupportedOperationException("op not compat with " + operator);
        return getValue(child);
      });
      return expectedPath + operator + values;
    }
    throw new UnsupportedOperationException();
  }

  private static String mapToCommaSeparatedValues(List<POp> pOps, Function<POp, String> mapFun) {
    return pOps.stream().map(mapFun).collect(Collectors.joining(","));
  }

  private static String getOperator(POpType pOpType) {
    String abbrev;
    if (pOpType == GT) abbrev = GT.toString();
    else if (pOpType == GTE) abbrev = GTE.toString();
    else if (pOpType == LT) abbrev = LT.toString();
    else if (pOpType == LTE) abbrev = LTE.toString();
    else throw new UnsupportedOperationException();
    return "=" + abbrev + "=";
  }

  private static String getValue(POp pop) {
    return URLEncoder.encode(pop.getValue().toString(), StandardCharsets.UTF_8);
  }

  private static String getPath(POp pop) {
    List<String> propRef = (pop.op() == NOT || pop.op() == OR)
        ? pop.children().get(0).getPropertyRef().getPath()
        : pop.getPropertyRef().getPath();
    return URLEncoder.encode(String.join(".", propRef), StandardCharsets.UTF_8);
  }
}
