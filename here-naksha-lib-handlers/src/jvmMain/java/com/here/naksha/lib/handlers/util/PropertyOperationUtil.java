/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
package com.here.naksha.lib.handlers.util;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.request.query.*;

public class PropertyOperationUtil {

  private PropertyOperationUtil() {
  }

  public static Set<PQuery> disablePQueriesInRequest(@NotNull RequestQuery requestQuery, @NotNull F1<Boolean, PQuery> shouldDisable) {
    IPropertyQuery rootPropertyQuery = requestQuery.getProperties();
    if (rootPropertyQuery != null) {
      // if there is only single PQuery in the whole request, disable without tree traversal by simply removing it (set to null)
      if (rootPropertyQuery instanceof PQuery rootPQuery && shouldDisable.call(rootPQuery)) {
        requestQuery.setProperties(null);
        return Set.of(rootPQuery);
      } else {
        // if there is a tree (not a PQuery) under `requestQuery.properties` - traverse the tree and logically disable matching pQuery
        HashSet<PQuery> disabledProperties = new HashSet<>();
        disablePropertyInPropertyQueryTree(rootPropertyQuery, null, shouldDisable, disabledProperties);
        return disabledProperties;
      }
    }
    // root property query is null -> no disabled property queries -> empty set
    return Collections.emptySet();
  }

  /**
   * @param current            Currently traversed node
   * @param parent             Parent containing current node (can be null for first iteration, should be checked on call-site)
   * @param removalCondition   If evaluates to true, it effectively disables the check by replacing it with `true-ish` query
   * @param disabledProperties Set of so-far disabled property queries
   */
  // TODO CASL-1123: this can be improved - we could inline "always true" statement such as AND(PTrue, PTrue) or OR(PTrue, PFalse)
  // in such cases we can simply remove the node - in edge cases, we could end up without IPropertyQuery at all (if all gets resolved)
  private static void disablePropertyInPropertyQueryTree(
      @NotNull IPropertyQuery current, @Nullable IPropertyQuery parent, F1<Boolean, PQuery> removalCondition, Set<PQuery> disabledProperties
  ) {
    switch (current) {
      case PAnd pAnd -> pAnd.forEach(andChild -> disablePropertyInPropertyQueryTree(andChild, pAnd, removalCondition, disabledProperties));
      case POr pOr -> pOr.forEach(orChild -> disablePropertyInPropertyQueryTree(orChild, pOr, removalCondition, disabledProperties));
      case PNot pNot -> disablePropertyInPropertyQueryTree(pNot.getQuery(), pNot, removalCondition, disabledProperties);
      case PQuery currentyPQuery when removalCondition.call(currentyPQuery) -> {
        if (parent instanceof PAnd pAndParent) {
          pAndParent.remove(current);
          pAndParent.add(PTrue.INSTANCE);
          disabledProperties.add(currentyPQuery);
        } else if (parent instanceof POr pOrParent) {
          pOrParent.remove(current);
          pOrParent.add(PTrue.INSTANCE);
          disabledProperties.add(currentyPQuery);
        } else if (parent instanceof PNot pNotParent) {
          pNotParent.setQuery(PFalse.INSTANCE);
          disabledProperties.add(currentyPQuery);
        }
      }
      default -> {
        // unhandled type / not matching pQuery => stop traversal without failing
      }
    }
  }
}
