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

import com.here.naksha.lib.core.lambdas.F1;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import naksha.model.request.RequestQuery;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.PAnd;
import naksha.model.request.query.PNot;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import org.jetbrains.annotations.NotNull;

public final class PropertyOperationUtil {

  private PropertyOperationUtil() {
  }

  /**
   * Traverses the property query tree of the given {@link RequestQuery} and disables any {@link PQuery} nodes that match the provided predicate.
   * <p>
   * The removed queries are collected and returned as a set.
   * <b>Important:</b> this method <b>mutates</b> the given {@code requestQuery}.
   * After execution, {@code requestQuery.getProperties()} may reference a different {@link IPropertyQuery} object than before or even be {@code null}, reflecting the removal of matching queries.
   * <p>
   * If the request has no property query, the returned set will be empty and the request is left unchanged.
   *
   * @param requestQuery  the request whose property query tree is to be traversed and modified
   * @param shouldDisable a predicate that determines whether a {@link PQuery} should be removed
   * @return a set containing all {@link PQuery} instances that were removed from the property query tree; returns an empty set if no queries were removed
   */
  public static Set<PQuery> disablePQueriesInRequest(@NotNull RequestQuery requestQuery, @NotNull F1<Boolean, PQuery> shouldDisable) {
    IPropertyQuery rootPropertyQuery = requestQuery.getProperties();
    if (rootPropertyQuery != null) {
      HashSet<PQuery> disabledProperties = new HashSet<>();
      IPropertyQuery newRootPropertyQuery = disablePropertyInPropertyQueryTree(
          rootPropertyQuery, shouldDisable, disabledProperties
      ).orElse(null);
      requestQuery.setProperties(newRootPropertyQuery);
      return disabledProperties;
    }
    // root property query is null -> no disabled property queries -> empty set
    return Collections.emptySet();
  }

  /**
   * @param current            Currently traversed node
   * @param removalCondition   Predicate that determines whether a {@link PQuery} should be disabled
   * @param disabledProperties Set of so-far disabled property queries
   * @return an {@link Optional} containing the updated query node, or an empty optional if the node is removed as a result of disabling
   */
  private static Optional<IPropertyQuery> disablePropertyInPropertyQueryTree(
      @NotNull IPropertyQuery current,
      @NotNull F1<Boolean, PQuery> removalCondition,
      @NotNull Set<PQuery> disabledProperties
  ) {
    if (current instanceof PAnd) {
      return handleCompoundQuery(
          (PAnd) current,
          removalCondition,
          disabledProperties,
          PAnd::new
      );
    }
    if (current instanceof POr) {
      return handleCompoundQuery(
          (POr) current,
          removalCondition,
          disabledProperties,
          POr::new
      );
    }
    if (current instanceof PNot) {
      PNot pNot = (PNot) current;
      return disablePropertyInPropertyQueryTree(
          pNot.getQuery(), removalCondition, disabledProperties
      ).flatMap(pq -> Optional.of(new PNot(pq)));
    }
    if (current instanceof PQuery) {
      PQuery currentPQuery = (PQuery) current;
      if (removalCondition.call(currentPQuery)) {
        disabledProperties.add(currentPQuery);
        return disabledPropertyQuery();
      }
    }
    return Optional.of(current);
  }

  private static Optional<IPropertyQuery> disabledPropertyQuery() {
    return Optional.empty();
  }

  private static boolean allChildrenDisabled(List<Optional<IPropertyQuery>> children) {
    return children.stream().allMatch(Optional::isEmpty);
  }

  private static List<IPropertyQuery> removeDisabledChildren(List<Optional<IPropertyQuery>> children) {
    return children.stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private static <T extends List<IPropertyQuery> & IPropertyQuery> Optional<IPropertyQuery> handleCompoundQuery(
      T compoundQuery,
      F1<Boolean, PQuery> removalCondition,
      Set<PQuery> disabledProperties,
      Supplier<T> constructor
  ) {
    List<Optional<IPropertyQuery>> newChildren = disablePropertyInChildrenQueryTree(
        compoundQuery,
        removalCondition,
        disabledProperties
    );
    if (!compoundQuery.isEmpty() && allChildrenDisabled(newChildren)) {
      return disabledPropertyQuery();
    }
    compoundQuery = constructor.get();
    compoundQuery.addAll(removeDisabledChildren(newChildren));
    return Optional.of(compoundQuery);
  }

  private static <T extends List<IPropertyQuery> & IPropertyQuery> List<Optional<IPropertyQuery>> disablePropertyInChildrenQueryTree(
      T compoundQuery,
      F1<Boolean, PQuery> removalCondition,
      Set<PQuery> disabledProperties
  ) {
    return compoundQuery.stream()
        .map(child -> disablePropertyInPropertyQueryTree(
            child, removalCondition, disabledProperties
        ))
        .collect(Collectors.toList());
  }
}
