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

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.core.models.storage.POpType.*;

import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.POpType;
import com.here.naksha.lib.core.models.storage.PRef;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class POpToQuery {

  static String p0pToQuery(POp pOp) {
    if (pOp.op() == AND)
      return pOp.children().stream().map(POpToQuery::p0pToQuery).collect(Collectors.joining("&"));
    else return pOpToTrop(pOp).resolve();
  }

  static Trop pOpToTrop(POp pOp) {
    if (pOp.op() == AND) throw unchecked(new UnsupportedOperationException());
    if (pOp.op() == OR) return or(pOp);
    if (pOp.op() == NOT) return not(pOp);
    if (pOp.op() == EXISTS) return exists(pOp);
    return simpleLeafOperator(pOp);
  }

  static Trop or(POp pOp) {
    validateChildrenCountAtLeastOne(pOp);
    return pOp.children().stream()
        .map(POpToQuery::pOpToTrop)
        .reduce((l, r) -> {
          if (!Objects.equals(l.operator, r.operator))
            throw unsupportedOperator(l.operator + "combined with" + r.operator);
          if (!Objects.equals(l.path, r.path) || Objects.isNull(l.path))
            throw unchecked(new UnsupportedOperationException("Path refs have to be equal for OR"));
          return new Trop(l.operator, l.path, String.join(",", l.valuesToString, r.valuesToString));
        })
        .get();
  }

  static Trop not(POp pOp) {
    validateChildrenCount(pOp, 1);
    Trop trop = pOpToTrop(pOp.children().get(0));
    String newOperator =
        switch (trop.operator) {
          case "=" -> "!=";
          case "!=" -> "=";
          default -> throw unsupportedOperator(trop.operator);
        };
    return new Trop(newOperator, trop.path, trop.valuesToString);
  }

  //
  // Leaf operators
  //
  static Trop exists(POp pOp) {
    validateChildrenCount(pOp, 0);
    return new Trop("!=", pOp.getPropertyRef(), ".null");
  }

  static Trop simpleLeafOperator(POp pOp) {
    String operator = simpleLeafOperators.get(pOp.op());
    if (operator == null) throw unchecked(new UnsupportedOperationException(pOp.op() + " not supported"));
    validateChildrenCount(pOp, 0);
    return new Trop(operator, pOp.getPropertyRef(), pOp.getValue().toString());
  }

  private static final Map<POpType, String> simpleLeafOperators = Map.of(
      EQ, "=",
      GT, "=gt=",
      GTE, "=gte=",
      LT, "=lt=",
      LTE, "=lte=",
      CONTAINS, "=cs=");

  private static void validateChildrenCount(POp pOp, int count) {
    List<@NotNull POp> children = pOp.children();
    if (children == null && count == 0) return;
    if (children != null && children.size() == count) return;
    throw unchecked(new UnsupportedOperationException());
  }

  private static void validateChildrenCountAtLeastOne(POp pOp) {
    if (pOp.children() == null || pOp.children().isEmpty()) throw unchecked(new UnsupportedOperationException());
  }

  private static RuntimeException unsupportedOperator(@Nullable String sign) {
    return unchecked(new UnsupportedOperationException(sign + " not supported in the operation"));
  }

  private record Trop(@NotNull String operator, @Nullable PRef path, @NotNull String valuesToString) {
    public String resolve() {
      if (path == null) throw new UnsupportedOperationException();
      String pathEncoded = URLEncoder.encode(String.join(".", path.getPath()), StandardCharsets.UTF_8);
      String valueEncoded = URLEncoder.encode(String.join(",", valuesToString), StandardCharsets.UTF_8);
      return pathEncoded + operator + valueEncoded;
    }
  }
}
